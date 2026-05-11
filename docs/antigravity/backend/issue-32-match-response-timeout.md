# Issue-32: Match response timeout settlement

## 📌 Feature Description

매칭 성사 후 10초 안에 수락/거절 응답을 완료하지 않은 유저를 timeout으로 정산하는 로직을 구현합니다.

현재 issue-30에서 accept/reject API와 유저별 응답 상태(`PENDING`, `ACCEPTED`, `REJECTED`, `TIMEOUT`)는 구현되어 있습니다. 이번 이슈는 10초 응답 윈도우가 끝났을 때 남아 있는 `PENDING` 유저를 `TIMEOUT`으로 처리하고, 정책에 따라 수락한 유저는 기존 `entryTime`으로 큐 최우선 복귀시키는 작업입니다.

핵심 정책은 다음과 같습니다.

- 양쪽 모두 `ACCEPTED`면 이미 매칭 성공 처리되므로 timeout 정산 대상이 아닙니다.
- `ACCEPTED + PENDING`이면 `PENDING` 유저는 `TIMEOUT`, `ACCEPTED` 유저는 기존 `entryTime`으로 큐 복귀합니다.
- `REJECTED + PENDING`이면 `PENDING` 유저는 `TIMEOUT`, 두 유저 모두 큐에서 이탈합니다.
- `PENDING + PENDING`이면 두 유저 모두 `TIMEOUT` 처리하고 큐에서 이탈합니다.
- timeout 정산도 accept/reject와 동일하게 `matchId` 기준 Redis lock으로 직렬화합니다.

### timeout trigger 후보 흐름

#### 1. 클라이언트가 timeout API 호출

```mermaid
flowchart TD
    A["match_found 수신"] --> B["클라이언트 모달 10초 표시"]
    B --> C["10초 동안 응답 없음"]
    C --> D["클라이언트가 timeout API 호출"]
    D --> E["서버 timeout 정산"]
```

#### 2. Redis TTL 만료 이벤트 사용

```mermaid
flowchart TD
    A["match session Redis key 저장"] --> B["TTL 만료"]
    B --> C["Redis keyspace notification"]
    C --> D["서버 subscriber 수신"]
    D --> E["timeout 정산 시도"]
```

#### 3. Message Queue / Delayed Queue 사용

```mermaid
flowchart TD
    A["match_found 생성"] --> B["10초 delay message 발행"]
    B --> C["MQ가 10초 후 message 전달"]
    C --> D["timeout worker consume"]
    D --> E["timeout 정산"]
```

#### 4. Redis ZSET deadline index + 서버 scheduler + Lua claim 사용

```mermaid
flowchart TD
    A["match session 생성"] --> B["pending ZSET에 deadline 저장"]

    subgraph CLAIM["claim 보장 범위: scheduler 중복 처리 방지"]
        B --> C["scheduler가 due matchId 조회"]
        C --> D["Lua claim"]
        D --> E["pending ZREM + processing ZADD 원자 처리"]
        E --> F["claim 성공 scheduler만 진행"]
    end

    subgraph LOCK["matchId lock 보장 범위: session 동시 수정 방지"]
        F --> G["matchId lock 획득"]
        G --> H["timeoutWithLock(matchId)"]
        H --> I["accept/reject/timeout read-modify-write 직렬화"]
    end

    I --> J["정산 성공 또는 이미 종료 no-op"]
    J --> K["processing ZSET에서 제거 ack"]
```

역할은 분리됩니다.

- Lua claim: 여러 scheduler 중 하나만 timeout job을 가져가게 합니다.
- matchId lock: accept/reject/timeout이 같은 match session을 동시에 수정하지 못하게 합니다.

```mermaid
sequenceDiagram
    autonumber
    participant S1 as API-1 Scheduler
    participant S2 as API-2 Scheduler
    participant Pending as pending ZSET
    participant Processing as processing ZSET
    participant Lua as Lua claim
    participant Lock as matchId Lock
    participant Session as match session

    S1->>Pending: due match-1 조회
    S2->>Pending: due match-1 조회

    S1->>Lua: claim match-1
    Lua->>Pending: ZREM match-1
    Lua->>Processing: ZADD match-1 processingExpireAt
    Lua-->>S1: claim success

    S2->>Lua: claim match-1
    Lua-->>S2: claim failed

    S1->>Lock: lock match:session:lock:match-1
    S1->>Session: timeout 정산
    S1->>Lock: unlock match-1
    S1->>Processing: ack, ZREM match-1
```

processing 중 서버가 죽으면 job은 processing ZSET에 남습니다. processing 만료 시간이 지나면 복구 scheduler가 다시 pending으로 이동시켜 재처리할 수 있습니다.

```mermaid
flowchart TD
    A["processing ZSET"] --> B{"processingExpireAt <= now"}
    B -->|"yes"| C["Lua reclaim"]
    C --> D["processing -> pending 원자 이동"]
    D --> E["다음 scheduler tick에서 재처리"]
    B -->|"no"| F["아직 처리 중으로 간주"]
```

## 📚 Tasks

### 1. timeout 추적 구조 설계

- [x] timeout 대상 matchId를 관리할 Redis 자료구조 결정
  - `match:response:timeout:pending` ZSET
    - score: timeout deadline epoch millis
    - member: matchId
  - `match:response:timeout:processing` ZSET
    - score: processing expire epoch millis
    - member: matchId
- [x] pending/processing 이동을 Lua script로 원자 처리
- [x] processing에 남은 작업을 pending으로 복구하는 reclaim 정책 정의
- [x] 세션 TTL 12초와 응답 제한 10초의 관계 정리
- [x] timeout 처리 완료/세션 완료 시 pending/processing index 제거 정책 정의
- [x] timeout 관련 상수 위치 정리
  - 응답 제한 시간 10초
  - session TTL 12초
  - scheduler batch size
  - processing lease 시간
- [x] 시간 계산은 테스트 가능하도록 현재 시각 주입 방식 검토

#### 설계 결과

- timeout 대상은 단일 ZSET이 아니라 `pending` / `processing` ZSET으로 분리합니다.
  - `pending`: 아직 scheduler가 가져가지 않은 timeout 후보
  - `processing`: scheduler가 claim 후 처리 중인 timeout 후보
- `pending -> processing` 이동은 Lua script로 원자 처리합니다.
  - 여러 API 인스턴스 scheduler가 같은 due matchId를 조회해도 하나만 claim에 성공합니다.
  - claim은 scheduler 간 timeout job 중복 처리를 줄이는 역할입니다.
- `processing -> pending` 복구도 Lua script로 원자 처리합니다.
  - claim 후 서버가 죽으면 matchId는 processing에 남습니다.
  - `processingExpireAt`이 지난 job은 reclaim 대상이 되고, 다음 scheduler tick에서 재처리됩니다.
- session 동시 수정은 여전히 `match:session:lock:{matchId}`로 보호합니다.
  - claim은 timeout scheduler 간 중복 처리 방지입니다.
  - lock은 accept/reject/timeout이 같은 session을 동시에 read-modify-write 하지 못하게 하는 보호 장치입니다.
- 시간 정책은 기존 프로젝트 정책을 따릅니다.
  - 클라이언트 응답 윈도우: 10초
  - Redis match session TTL: 12초
  - TTL 12초는 10초 응답 윈도우에 네트워크/스케줄링 지연 버퍼를 더한 값입니다.
- 상수는 `MatchingConstants`를 우선 확장합니다.
  - 기존 `MATCH_SESSION_TTL_SECONDS = 12`는 유지합니다.
  - timeout 응답 제한 시간, timeout ZSET key, Lua script path는 matching 모듈 공통 상수로 둡니다.
  - scheduler interval, batch size, processing lease time은 운영 조정 가능성이 있어 설정값 분리도 함께 검토합니다.
- timeout deadline 계산은 테스트 가능해야 하므로 `Clock` 주입을 사용합니다.
  - `smite-api`의 `ClockConfig`에 의존하지 않습니다.
  - `smite-matching` 모듈에 `@ConditionalOnMissingBean(Clock.class)` 기반 Clock 설정을 추가합니다.
  - core 모듈에는 Spring config를 두지 않습니다.
  - matching 모듈 timeout 구현에서는 `System.currentTimeMillis()` 직접 호출을 피하고 `clock.millis()` 기준으로 계산합니다.
- timeout index cleanup 정책은 다음과 같습니다.
  - 매칭이 최종 `ACCEPTED` 또는 `DECLINED`로 끝나면 pending/processing 양쪽에서 제거를 시도합니다.
  - cleanup 실패는 accept/reject API 성공을 깨지 않습니다.
  - 남은 timeout job은 scheduler가 나중에 claim 후 session 상태를 보고 no-op/ack로 정리합니다.

### 2. timeout index 저장소 구현

- [x] `MatchTimeoutStore` 포트 추가
  - matching service/scheduler는 Redis 구현체에 직접 의존하지 않음
- [x] `RedisMatchTimeoutStore` 구현
  - Lua script와 Redis ZSET 명령은 infrastructure에 격리
- [x] due pending matchId 조회
- [x] Lua claim 구현
  - pending에 있는 due matchId만 processing으로 이동
  - 이미 다른 scheduler가 가져간 matchId는 claim 실패 처리
- [x] Lua reclaim 구현
  - processing 만료 시간이 지난 matchId를 pending으로 복구
- [x] 처리 완료된 matchId ack 제거
  - processing ZSET에서 제거
- [x] 세션이 먼저 완료된 경우 pending/processing cleanup
  - pending과 processing 둘 다 제거 시도
- [x] batch size 정책 결정
- [x] Lua script 파일 경로와 로딩 방식 결정
- [x] Redis 통합 테스트 작성

#### 구현 결과

- `MatchTimeoutStore` 포트를 추가했습니다.
  - 위치: `smite-matching/src/main/java/com/sang/smite/matching/repository/MatchTimeoutStore.java`
  - matching service/scheduler는 timeout index 저장소를 포트로 의존합니다.
- `RedisMatchTimeoutStore` 구현체를 추가했습니다.
  - 위치: `smite-matching/src/main/java/com/sang/smite/matching/infrastructure/RedisMatchTimeoutStore.java`
  - Redis ZSET과 Lua script 실행은 infrastructure에 격리했습니다.
- timeout ZSET key와 Lua script path를 `MatchingConstants`에 추가했습니다.
  - `match:response:timeout:pending`
  - `match:response:timeout:processing`
  - `scripts/timeout_claim.lua`
  - `scripts/timeout_reclaim.lua`
- `timeout_claim.lua`를 추가했습니다.
  - pending에 matchId가 있고 deadline이 지났을 때만 claim 성공
  - claim 성공 시 `pending ZREM + processing ZADD`를 Redis 안에서 원자 처리
- `timeout_reclaim.lua`를 추가했습니다.
  - processing에 matchId가 있고 lease가 만료되었을 때만 reclaim 성공
  - reclaim 성공 시 `processing ZREM + pending ZADD`를 Redis 안에서 원자 처리
- timeout ZSET과 Lua script 실행은 `StringCodec`으로 통일했습니다.
  - script ARGV 숫자 비교와 ZSET member 조회가 같은 codec 기준으로 동작해야 하기 때문입니다.
- Redis 통합 테스트를 추가했습니다.
  - due pending 조회
  - batch size 제한
  - claim 성공
  - 중복 claim 실패
  - deadline 이전 claim 실패
  - ack 제거
  - pending/processing cleanup
  - expired processing reclaim
  - lease 만료 전 reclaim 실패
- 검증 명령:
  - `:smite-matching:test`

### 3. MatchFoundService timeout 등록

- [ ] 매칭 세션 생성 시 timeout deadline 등록
- [ ] `createdAt + 10_000ms` 기준으로 pending ZSET에 저장
- [ ] 세션 저장 성공 후 timeout index 등록 순서 보장
- [ ] 기존 `match_found` 이벤트 발행 흐름 유지

### 4. MatchResponseProcessor timeout 정산 구현

- [ ] `timeoutWithLock(matchId)` 추가
- [ ] accept/reject와 동일한 `match:session:lock:{matchId}` 사용
- [ ] `FOUND` 상태 세션만 timeout 정산
- [ ] `PENDING` 유저를 `TIMEOUT`으로 변경
- [ ] `ACCEPTED` 유저는 기존 `entryTime`으로 큐 복귀
- [ ] `REJECTED`/`TIMEOUT` 유저는 큐 이탈
- [ ] 세션 status를 `TIMEOUT`으로 변경
- [ ] 이미 `ACCEPTED`/`DECLINED`/`TIMEOUT`인 세션은 no-op 처리
- [ ] 세션 없음/TTL 만료 케이스는 no-op 가능 여부 결정 후 index 정리
- [ ] timeout 정산 로직이 accept/reject의 큐 복귀 정책과 중복되지 않도록 공통 helper 정리

### 5. accept/reject 완료 시 timeout index 정리

- [ ] 양쪽 accept로 `ACCEPTED` 된 경우 timeout index 제거
- [ ] accept/reject 조합으로 `DECLINED` 정산된 경우 timeout index 제거
- [ ] 한쪽만 응답해 `FOUND`가 유지되는 경우 timeout index 유지
- [ ] timeout scheduler가 이미 claim한 뒤라면 timeout 정산 쪽에서 lock 획득 후 no-op/ack 처리
- [ ] accept/reject 완료 후 timeout index cleanup 실패 시 API 성공은 유지
  - 로그/지표로 관측
  - scheduler가 나중에 no-op/ack로 후속 정리

### 6. timeout scheduler 구현

- [ ] scheduler 활성화 설정 추가
  - interval
  - batch size
  - processing lease time
- [ ] 주기적으로 due pending matchId 조회
- [ ] due matchId별 Lua claim 시도
- [ ] claim 성공한 matchId만 timeout 정산 호출
- [ ] timeout 정산 성공 또는 no-op 후 processing ack 제거
- [ ] processing 만료 작업 reclaim 후 다음 tick에서 재시도
- [ ] claim은 scheduler 중복 처리를 줄이고, matchId lock은 accept/reject/timeout 경합을 보호하도록 역할 분리
- [ ] 개별 matchId 처리 실패가 전체 batch를 중단하지 않도록 처리

### 7. 테스트 작성

- [ ] `MatchSession` timeout 상태 변경 domain unit test
- [ ] `MatchResponseProcessor` timeout service unit test
- [ ] A accept, B pending → timeout 시 A 큐 복귀, B timeout
- [ ] A reject, B pending → timeout 시 둘 다 큐 이탈
- [ ] A/B pending → timeout 시 둘 다 큐 이탈
- [ ] 이미 `ACCEPTED` 세션 timeout → no-op
- [ ] 이미 `DECLINED` 세션 timeout → no-op
- [ ] 세션 없음 timeout → no-op 또는 index 정리
- [ ] timeout과 accept/reject 경합 케이스 검증
- [ ] `RedisMatchTimeoutStore` Redis/Testcontainers 통합 테스트
- [ ] 여러 scheduler가 같은 due matchId를 조회해도 하나만 claim 성공
- [ ] claim 후 서버 crash 상황에서 processing reclaim 가능
- [ ] scheduler batch 처리 중 일부 실패해도 나머지 처리 계속되는지 검증

### 8. 관측 지표 및 부하 테스트

- [ ] issue-30에서 보류한 accept/reject 기본 지표 반영
  - accept API 호출 수
  - accept 성공/실패 수
  - reject API 호출 수
  - reject 성공/실패 수
  - 양쪽 수락 완료 수
  - 세션 만료/없음 실패 수
  - lock 획득 실패 수
- [ ] timeout 처리 수
- [ ] timeout 성공/실패 수
- [ ] timeout no-op 처리 수
- [ ] timeout으로 큐 복귀한 유저 수
- [ ] timeout scheduler 처리량/backlog 지표
- [ ] pending/processing backlog 지표
- [ ] claim 성공/실패 수
- [ ] reclaim 처리 수
- [ ] scheduler scan duration / batch processing duration 지표
- [ ] timeout deadline 대비 실제 처리 지연 시간 지표
- [ ] accept/reject/timeout 경합 부하 테스트 작성

### 9. 문서 갱신

- [ ] `docs/project/policy.md` timeout 정산 정책 확인
- [ ] `docs/project/matching.md` timeout scheduler/index 구조 반영
- [ ] issue-30에서 후속 이슈로 남긴 timeout note와 정합성 확인

### 10. 최종 검증

- [ ] `:smite-core:test`
- [ ] `:smite-matching:test`
- [ ] `:smite-api:test`
- [ ] 기존 accept/reject API 회귀 확인
- [ ] 기존 `match_found` SSE 흐름 회귀 확인

## 📝 Note

### timeout trigger 방식 비교

#### 1. 클라이언트 timeout API 호출

클라이언트가 모달 10초 타이머가 끝났을 때 서버에 timeout API를 호출하는 방식입니다.

장점:

- 구현이 단순해 보입니다.
- 클라이언트 모달 타이머와 timeout 호출 시점이 직관적으로 맞습니다.

단점:

- 브라우저가 닫히거나 네트워크가 끊기면 timeout 호출이 오지 않습니다.
- 악의적 클라이언트가 timeout 호출을 보내지 않을 수 있습니다.
- 서버가 10초 정산을 책임진다고 보기 어렵습니다.

결론:

- primary timeout trigger로는 사용하지 않습니다.
- 클라이언트는 화면 표시만 담당하고, timeout 정산은 서버가 책임지는 방향이 맞습니다.

#### 2. Redis TTL 만료 이벤트

Redis key가 만료될 때 발생하는 keyspace notification을 받아 timeout을 처리하는 방식입니다.

장점:

- TTL과 timeout이 자연스럽게 연결되어 보입니다.
- 별도 timeout index가 없어도 될 것처럼 보입니다.

단점:

- Redis keyspace notification 설정이 필요합니다.
- 이벤트 전달 보장이 강하지 않습니다.
- subscriber가 죽어 있으면 이벤트를 놓칠 수 있습니다.
- key가 만료된 뒤에는 정산에 필요한 세션 데이터가 이미 사라졌을 수 있습니다.
- 현재 세션 TTL은 12초이고 정책 timeout은 10초라 정확히 같은 개념이 아닙니다.

결론:

- timeout 정산 trigger로 쓰기에는 위험합니다.
- 세션 cleanup 보조 용도 정도로만 고려합니다.

#### 3. Message Queue / Delayed Queue

match_found 시점에 10초 delayed message를 넣고, worker가 메시지를 받아 timeout을 처리하는 방식입니다.

장점:

- 이벤트 기반에 가깝습니다.
- Kafka, RabbitMQ, SQS 같은 도구를 쓰면 재시도와 DLQ를 설계하기 좋습니다.
- 멀티 인스턴스 worker 구조와 잘 맞습니다.

단점:

- 현재 프로젝트에 별도 MQ가 없습니다.
- 인프라와 운영 복잡도가 커집니다.
- Redis로 delayed queue를 직접 만들면 결국 ZSET + scheduler와 비슷해집니다.

결론:

- 대규모 운영 단계에서는 좋은 선택지가 될 수 있습니다.
- 현재 V1에서는 Redis만으로 해결하는 편이 단순합니다.

#### 4. Redis ZSET deadline index + 서버 scheduler + Lua claim

timeout 대상 matchId를 Redis ZSET에 deadline 기준으로 저장하고, 서버 scheduler가 due matchId를 조회해 정산하는 방식입니다.

처음에는 단순히 `due 조회 -> matchId lock -> timeout 정산 -> index 제거` 흐름도 가능해 보입니다. 하지만 멀티 인스턴스에서는 여러 scheduler가 같은 due matchId를 동시에 볼 수 있습니다.

```text
API-1 Scheduler: match-1 due 조회
API-2 Scheduler: match-1 due 조회
API-3 Scheduler: match-1 due 조회
```

Lua claim이 없으면 세 서버가 모두 `matchId lock` 획득을 시도합니다. 최종 정산은 lock 때문에 하나만 성공하더라도, 나머지 서버는 불필요하게 lock 대기/실패/no-op 처리를 반복합니다.

```text
Lua claim 미적용 시 문제:
- 같은 timeout job을 여러 scheduler가 동시에 처리하려고 함
- lock 경합이 늘어남
- timeout backlog가 많아질수록 불필요한 재시도 비용이 커짐
- 어떤 서버가 처리 중인지 Redis 자료구조만 보고 알기 어려움
- 단순 ZREM으로 먼저 제거하면 서버 crash 시 timeout job이 유실될 수 있음
```

그래서 이번 이슈에서는 Lua claim을 적용합니다. claim은 timeout 정산 자체가 아니라, “이 timeout job은 한 scheduler만 처리하라”는 작업 소유권을 Redis에서 먼저 정리하는 단계입니다.

이번 이슈에서는 단일 ZSET이 아니라 `pending ZSET`과 `processing ZSET`을 나눕니다.

```text
pending    = 아직 아무 scheduler도 가져가지 않은 timeout 후보
processing = 어떤 scheduler가 처리 중이라고 표시한 timeout 후보
```

전체 흐름은 다음과 같습니다.

```text
1. match_found 생성
2. pending ZSET에 matchId와 timeout deadline 저장
3. scheduler가 deadline이 지난 matchId 조회
4. Lua claim으로 pending -> processing 원자 이동
5. claim에 성공한 scheduler만 matchId lock 획득
6. timeout 정산
7. 성공 또는 이미 종료 no-op이면 processing에서 ack 제거
8. 처리 중 서버가 죽으면 processing 만료 후 pending으로 reclaim
```

장점:

- 현재 매칭 시스템이 이미 Redis를 중심으로 동작하므로 구조가 잘 맞습니다.
- 10초 timeout deadline과 12초 session TTL을 분리해서 관리할 수 있습니다.
- 서버가 timeout 정산을 책임집니다.
- 멀티 인스턴스에서도 Lua claim으로 같은 timeout job을 한 서버만 가져가게 할 수 있습니다.
- `matchId` lock으로 accept/reject/timeout이 같은 세션을 동시에 수정하지 못하게 막을 수 있습니다.
- processing ZSET이 있어 claim 후 서버가 죽어도 reclaim으로 복구할 수 있습니다.
- timeout backlog, 처리량, 지연 시간을 지표로 관측하기 좋습니다.

단점:

- scheduler polling이 필요합니다.
- scheduler 지연이 생기면 timeout 정산도 늦어질 수 있습니다.
- pending/processing/reclaim 구조가 단일 ZSET보다 복잡합니다.
- Lua script 테스트와 Redis 통합 테스트가 필요합니다.

결론:

- V1에서는 이 방식을 우선 채택합니다.
- 구현 방향은 `due 조회 -> Lua claim -> matchId lock -> timeout 정산 -> 성공/no-op 후 ack`입니다.
- 서버 crash 복구는 `processing 만료 조회 -> Lua reclaim -> pending 복귀 -> 다음 tick 재처리`로 처리합니다.

### claim과 lock의 차이

claim은 여러 서버 중 “이 timeout job은 내가 처리하겠다”고 표시하는 단계입니다.

예를 들어 API 서버가 2대면 둘 다 같은 `match-1` timeout 대상을 볼 수 있습니다.

```text
API-1: match-1 발견
API-2: match-1 발견
```

claim이 없으면 둘 다 처리하려고 합니다. 그래도 `matchId` lock이 있으면 최종 정산은 하나만 됩니다. 다만 불필요한 lock 대기와 실패가 늘어납니다.

이번 이슈에서는 단순 `ZREM` claim을 쓰지 않습니다.

```text
API-1: ZREM match-1 -> 1  // 처리권 획득
API-2: ZREM match-1 -> 0  // 이미 누가 가져감
```

하지만 `ZREM`을 먼저 하고 서버가 죽으면 문제가 생깁니다.

```text
API-1: ZREM 성공
API-1: timeout 정산 전에 crash
match-1은 index에서 사라졌는데 정산은 안 됨
```

그래서 pending에서 바로 제거하고 끝내지 않고, Lua script로 processing에 옮깁니다.

```text
1. pending에 match-1 존재
2. API-1이 Lua claim 성공
3. Redis 안에서 pending ZREM + processing ZADD를 한 번에 수행
4. API-2가 같은 match-1 claim 시도
5. pending에 없으므로 claim 실패
```

이렇게 하면 scheduler끼리는 같은 timeout job을 중복 처리하지 않습니다.

다만 claim은 scheduler끼리의 중복만 막습니다. 유저의 accept/reject API는 claim 대상이 아닙니다.

```text
API-1 Scheduler: match-1 timeout claim 성공
API-2 User API:  match-1 accept 요청
```

이 두 요청은 같은 match session을 수정합니다. 그래서 `matchId` lock은 여전히 필요합니다.

```text
claim이 보장하는 것:
- 여러 scheduler 중 하나만 timeout job을 가져간다.

matchId lock이 보장하는 것:
- accept/reject/timeout이 같은 session을 동시에 read-modify-write 하지 못한다.
- 10초 경계에서 accept와 timeout이 동시에 들어와도 session 상태가 한 번에 하나씩만 바뀐다.
```

### crash 복구

claim 후 서버가 죽으면 matchId는 processing ZSET에 남습니다.

```text
1. API-1이 Lua claim 성공
2. match-1이 processing으로 이동
3. API-1이 timeout 정산 전에 crash
4. match-1은 processing에 남아 있음
5. processingExpireAt이 지나면 reclaim 대상
6. Lua reclaim으로 processing -> pending 복구
7. 다음 scheduler tick에서 다시 claim 후 처리
```

이 구조는 단순히 pending에서 제거하는 방식보다 안전합니다. 작업을 누가 가져갔는지 표시하면서도, 처리 중 죽었을 때 다시 살릴 수 있기 때문입니다.
