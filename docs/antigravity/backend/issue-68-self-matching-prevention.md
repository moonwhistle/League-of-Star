# Issue 68. 셀프 매칭 방지 기능 구현

## 📌 Feature Description

매칭 queue 정상 진입 흐름에서는 `MatchQueueCommandService.joinQueue`가 `userStatusStore.setStatusIfAbsent(userId, MATCHING, STATUS_TTL_SECONDS)`를 먼저 호출해 동일 userId 중복 진입을 막는다. Redis queue도 tier별 ZSET member를 `userId`로 저장하므로 같은 tier queue 안에서는 동일 userId가 중복으로 쌓이기 어렵다.

다만 Redis queue snapshot 오염, 복구 과정, tier queue 불일치처럼 비정상 상태가 생기면 같은 userId의 `MatchTicket`이 서로 다른 tier queue에서 동시에 조회될 수 있다. 이슈 시작 시점의 `MatchPairingService.isMatchable`은 tier 차이만 판단했으므로, 이런 비정상 snapshot에서는 동일 userId끼리 `atomicPairRemove`와 `MatchFoundService.process`까지 진행될 수 있었다.

이번 이슈는 정상 queue 진입 정책은 유지하면서, pairing 단계에 동일 userId 후보 제외 방어선을 추가한다. 동일 userId 후보는 장애나 복구 대상이 아니라 후보 부적합으로 처리하고, 가능한 경우 다음 후보 탐색을 계속한다.

```mermaid
flowchart TD
    A[User match queue join 요청] --> B[setStatusIfAbsent MATCHING]
    B -->|실패| C[ALREADY_IN_QUEUE]
    B -->|성공| D[Redis tier queue에 MatchTicket 저장]
    D --> E[MatchEngineScheduler tick]
    E --> F[MatchPairingService loadSortedTickets]
    F --> G[entryTime 기준 FIFO 정렬]
    G --> H[userA 기준 후보 탐색]
    H --> I{userA.userId == userB.userId?}
    I -->|Yes| J[동일 userId 후보 skip]
    J --> K[다음 후보 탐색]
    I -->|No| L{tier range 충족?}
    L -->|No| K
    L -->|Yes| M[atomicPairRemove]
    M -->|실패| K
    M -->|성공| N[MatchFoundService.process]
```

핵심 정책은 다음과 같음.

- 정상 queue 진입의 동일 userId 중복 방지는 기존 `setStatusIfAbsent` 정책을 유지한다.
- pairing 단계에서도 동일 userId 후보는 matchable 후보로 보지 않는다.
- 동일 userId 후보 제외는 실패나 예외가 아니라 후보 부적합 skip으로 처리한다.
- 동일 userId 후보를 제외해도 scan loop와 다음 후보 탐색은 계속한다.
- 서로 다른 userId의 기존 FIFO, tier range, `atomicPairRemove`, match found 후처리 흐름은 변경하지 않는다.
- 동일 IP 기반 어뷰징 방지, client IP 수집, proxy/load balancer header 신뢰 정책은 이번 이슈 범위에 포함하지 않는다.
- 이번 이슈에서는 새 로그, metric, Grafana dashboard를 추가하지 않는다.

### Current Implementation Analysis

이슈 시작 시점의 구현 상태는 다음과 같음.

- `MatchQueueCommandService.joinQueue`는 queue 추가 전에 `userStatusStore.setStatusIfAbsent(userId, MATCHING, STATUS_TTL_SECONDS)`를 호출한다.
- `setStatusIfAbsent`가 실패하면 `MatchingErrorCode.ALREADY_IN_QUEUE`로 queue 진입을 거절한다.
- `RedisMatchQueueStore.add`는 tier별 ZSET에 `entryTime`을 score, `userId`를 member로 저장한다.
- `RedisMatchQueueStore.findAll`은 모든 tier queue를 조회한 뒤 `MatchTicket(userId, tierScore, entryTime)` 목록으로 변환한다.
- `MatchPairingService.loadSortedTickets`는 queue snapshot을 `entryTime` 오름차순으로 정렬한다.
- `MatchPairingService.findMatchableCandidates`는 이미 paired 처리된 userId와 `isMatchable` 실패 후보만 제외한다.
- `MatchPairingService.isMatchable`은 당시 userA 대기 시간 기반 tier 허용 범위와 userB tier 차이만 판단했다.
- `MatchPairingService.confirmPair`는 후보 두 유저를 `MatchQueueStore.atomicPairRemove`로 queue에서 원자 제거하고, 성공하면 `MatchFoundService.process`를 호출한다.

### Package Boundary

| 영역 | 패키지 | 책임 |
|------|--------|------|
| queue 진입 중복 방지 | `smite-matching` `command` | `setStatusIfAbsent`로 동일 userId 정상 중복 진입 차단 |
| 매칭 후보 탐색 | `smite-matching` `domain/service` | FIFO 정렬, 후보 선택, matchable 판정 |
| Redis queue 저장소 | `smite-matching` `repository`, `infrastructure/redis` | `MatchTicket` add/remove/findAll/atomicPairRemove |
| match found 후처리 | `smite-matching` `domain/service` | session 저장, timeout 등록, user status 전환, event 발행 |
| MatchTicket | `smite-core` `domain/match/domain` | userId, tierScore, entryTime value object |

- 이번 이슈의 주 구현 위치는 `smite-matching`의 `MatchPairingService`임.
- `MatchQueueCommandService`의 queue 진입 정책은 변경하지 않음.
- `MatchTicket` schema와 Redis queue key 구조는 변경하지 않음.
- Lua `atomic_pair_remove` 구조는 변경하지 않음.
- `MatchFoundService` 후처리와 Issue 66 복구 정책은 변경하지 않음.

### Matching Policy

| 상황 | 처리 |
|------|------|
| 정상 queue 진입에서 동일 userId가 이미 `MATCHING` | `ALREADY_IN_QUEUE`로 진입 거절 |
| 같은 tier queue에 동일 userId add | ZSET member 갱신 성격으로 중복 row처럼 쌓이지 않음 |
| queue snapshot에 동일 userId 티켓 2개가 존재 | pairing 후보에서 제외 |
| 동일 userId 후보 뒤에 다른 matchable 후보가 존재 | 동일 userId 후보는 skip하고 다음 후보와 매칭 시도 |
| 동일 userId 후보밖에 없음 | 이번 scheduler tick에서 매칭 성사 없음 |

정책 세부 기준은 다음과 같음.

- 동일 userId 판단은 `userA.userId().equals(userB.userId())`로 처리한다.
- 동일 userId 후보는 `atomicPairRemove` 호출 전 단계에서 제외한다.
- 동일 userId 후보 제외 시 queue 삭제, status 변경, session 생성, event 발행을 하지 않는다.
- 동일 userId 후보 제외는 복구 흐름이 아니므로 `MatchFoundService` 보상 helper를 호출하지 않는다.
- 동일 userId 후보가 skip되어도 userA는 같은 scan cycle 안에서 다음 후보를 계속 탐색한다.

### Scope Boundary

이번 이슈에 포함함.

- queue 진입 단계의 동일 userId 중복 방지 정책 확인
- Redis queue snapshot에 동일 userId 티켓이 중복 포함될 수 있는 비정상 경로 정리
- `MatchPairingService` 후보 판정에서 동일 userId 후보 제외
- 동일 userId 후보 제외 후 다음 후보 탐색 유지
- 동일 userId 후보만 존재할 때 매칭 미발생 검증
- `MatchPairingServiceTest` 회귀 테스트 추가
- 정책 문서와 checkpoint 문서 갱신

이번 이슈에서 제외함.

- 동일 IP 기반 어뷰징 방지 정책
- client IP 추출 및 proxy/load balancer header 신뢰 정책
- `MatchTicket` IP/hash 필드 추가
- Redis queue key 또는 Lua atomic remove 구조 변경
- queue 진입 API 계약 변경
- `MatchFoundService` 후처리 복구 정책 변경
- metric 추가
- Grafana dashboard 수정

## 📚 Tasks

### 1. 현재 queue 진입 중복 방지 정책 확인

- [x] `MatchQueueCommandService.joinQueue`의 `setStatusIfAbsent` 기반 동일 userId 중복 진입 차단 흐름 확인
- [x] `setStatusIfAbsent` 실패 시 `ALREADY_IN_QUEUE`로 queue add가 수행되지 않는지 확인
- [x] `RedisMatchQueueStore.add`가 tier별 ZSET member를 `userId`로 저장하는 구조 확인
- [x] 정상 API 흐름에서는 동일 userId가 중복 queue 진입하기 어렵다는 경계를 문서화

완료 기준은 다음과 같음.

- 정상 queue 진입 단계와 pairing 단계의 책임 경계가 분리되어 설명됨.
- 이번 이슈가 정상 진입 정책 변경이 아니라 pairing 단계 방어선 추가임이 문서화됨.

확인 결과는 다음과 같음.

- `MatchQueueCommandService.joinQueue`는 queue 추가 전에 `userStatusStore.setStatusIfAbsent(userId, MATCHING, STATUS_TTL_SECONDS)`를 호출함.
- `setStatusIfAbsent`가 `false`를 반환하면 `MatchingErrorCode.ALREADY_IN_QUEUE`를 던지고 `matchStore.add`를 호출하지 않음.
- `MatchQueueCommandServiceTest.joinQueue_already_matching`은 이미 매칭 중인 유저의 queue add 미호출을 검증함.
- `RedisMatchUserStatusStoreTest.setIfAbsent_alreadyExists`는 기존 status가 있으면 새 status 저장이 무시되고 `false`가 반환되는 것을 검증함.
- `RedisMatchQueueStore.add`는 tier별 ZSET에 `entryTime`을 score, `userId`를 member로 저장하므로 같은 tier queue 안에서는 동일 userId가 중복 row처럼 쌓이지 않음.
- 따라서 이번 이슈는 정상 queue 진입 정책 변경이 아니라, 비정상 queue snapshot이 pairing 단계까지 들어왔을 때 동일 userId 후보를 제외하는 방어선 추가임.

### 2. 동일 userId 중복 snapshot 비정상 경로 정리

- [x] 서로 다른 tier queue에 같은 userId ticket이 남을 수 있는 비정상 상황을 정리
- [x] queue 복구, tier 변경, Redis 데이터 불일치 같은 방어 대상 상황을 문서화
- [x] 동일 userId 중복 snapshot을 장애로 즉시 복구하지 않고 pairing 후보 부적합으로 처리하는 정책 확정

완료 기준은 다음과 같음.

- 동일 userId 후보 제외가 사용자 실패 응답이나 보상 로직이 아니라 scan 후보 판단임이 명확함.
- 동일 IP 기반 어뷰징 방지와 이번 이슈 범위가 분리됨.

확인 결과는 다음과 같음.

- queue add 경로는 정상 queue 진입, match found 후처리 실패 보상 복귀, 실패한 match response에서 수락 유저 재큐잉 흐름으로 제한됨.
- `MatchFoundService.restoreUserToQueue`는 `atomicPairRemove` 이후 후처리 실패 시 기존 `MatchTicket`을 그대로 queue에 복귀시킴.
- `MatchResponseResultService.returnAcceptedUserToQueue`는 한 명만 수락한 실패 세션에서 수락 유저를 기존 `entryTime`과 `tierScore`로 queue에 복귀시킴.
- 정상 정책상 동일 userId는 하나의 점유 status를 가지므로 중복 snapshot이 기대 흐름은 아님.
- 다만 Redis tier queue 데이터가 수동 수정되거나, 과거 tier ticket이 남은 상태에서 다른 tier ticket이 추가되거나, 복구/재큐잉 과정과 외부 데이터 불일치가 겹치면 `findAll` snapshot에 동일 userId가 서로 다른 tierScore로 포함될 수 있음.
- 이 경우 pairing 단계가 Redis 데이터를 즉시 정리하거나 사용자 실패 응답을 만들지는 않음. 동일 userId 후보를 matchable 후보에서 제외하고 다음 후보 탐색을 계속하는 방어 정책으로 처리함.
- 동일 IP 기반 어뷰징 방지는 IP 수집, header 신뢰, 개인정보 보관 정책이 필요하므로 이번 이슈 범위에서 제외함.

### 3. `MatchPairingService` 후보 판정 경계 확정

- [x] 동일 userId 제외 조건을 `isMatchable`에 둘지 `findMatchableCandidates`에 둘지 결정
- [x] 기존 tier range 판단과 동일 userId 판단의 순서를 정리
- [x] 동일 userId 후보가 `atomicPairRemove`로 넘어가지 않아야 한다는 정책 확정

완료 기준은 다음과 같음.

- 후보 부적합 조건이 한 곳에서 읽히도록 구현 위치가 정리됨.
- 기존 FIFO 정렬과 tier range 계산 정책은 변경되지 않음.

확정한 경계는 다음과 같음.

- 동일 userId 제외 조건은 `MatchPairingService.isMatchable`의 첫 번째 조건으로 둔다.
- `findMatchableCandidates`는 기존처럼 후보 순회, `pairedUserIds` 제외, `isMatchable` 호출 흐름만 유지한다.
- `isMatchable`은 동일 userId 여부를 먼저 확인하고, 서로 다른 userId인 경우에만 기존 대기 시간 기반 tier range 계산을 수행한다.
- 동일 userId 후보는 `findMatchableCandidates`에서 candidates 목록에 추가되지 않으므로 `confirmPair`와 `atomicPairRemove`로 넘어가지 않는다.
- 이 결정은 후보 판정 경계만 확정한 것이며, 실제 코드 변경과 회귀 테스트는 다음 task에서 진행한다.

### 4. 동일 userId 후보 제외 구현

- [x] `MatchPairingService.isMatchable`에서 동일 userId면 `false`를 반환하도록 구현
- [x] 동일 userId 후보 제외 후 기존 tier range 계산은 서로 다른 userId 후보에만 적용되도록 유지
- [x] 동일 userId 후보 제외 시 새 로그/metric을 추가하지 않음
- [x] `MatchQueueStore.atomicPairRemove` 호출 계약은 변경하지 않음

완료 기준은 다음과 같음.

- 동일 userId 후보는 `atomicPairRemove` 전에 제외됨.
- 서로 다른 userId 후보의 기존 매칭 흐름은 변경되지 않음.

구현 결과는 다음과 같음.

- `MatchPairingService.isMatchable` 시작 지점에 `userA.userId().equals(userB.userId())` 조건을 추가함.
- 동일 userId 후보는 `false`를 반환하므로 `findMatchableCandidates`의 candidates 목록에 추가되지 않음.
- 동일 userId가 아닌 후보만 기존 `getWaitTimeSeconds`, `calculateAllowedTierDiff`, `tierDiff` 계산을 수행함.
- 새 로그, metric, store method, Lua script 변경 없이 후보 판정만 변경함.

### 5. 동일 userId 후보 skip 후 다음 후보 탐색 테스트 추가

- [x] 동일 userId 후보가 먼저 있어도 다음 matchable 후보를 탐색하는 테스트 추가
- [x] 동일 userId 후보에 대해 `atomicPairRemove`가 호출되지 않는지 검증
- [x] 다음 후보에 대해 `atomicPairRemove`와 `MatchFoundService.process`가 호출되는지 검증

완료 기준은 다음과 같음.

- 동일 userId 후보 제외가 scan 중단으로 이어지지 않음.
- 가능한 다음 후보와 기존처럼 매칭이 성사됨.

구현 결과는 다음과 같음.

- `MatchPairingServiceTest.sameUserIdCandidateSkippedAndNextCandidateMatched`를 추가함.
- queue snapshot에 `userA(userId=1)`, `sameUser(userId=1)`, `userB(userId=2)`를 배치함.
- 동일 userId 후보인 `sameUser`에 대해서는 `atomicPairRemove(1, 10, 1, 11)`와 `MatchFoundService.process(userA, sameUser)`가 호출되지 않음을 검증함.
- 다음 후보인 `userB`에 대해서는 `atomicPairRemove(1, 10, 2, 10)`와 `MatchFoundService.process(userA, userB)`가 호출됨을 검증함.

### 6. 동일 userId 후보만 있을 때 매칭 미발생 테스트 추가

- [x] queue snapshot에 동일 userId 후보만 존재하는 테스트 추가
- [x] `MatchQueueStore.atomicPairRemove`가 호출되지 않는지 검증
- [x] `MatchFoundService.process`가 호출되지 않는지 검증
- [x] scan 결과 pair count가 `0`으로 기록되는지 검증

완료 기준은 다음과 같음.

- 동일 userId끼리는 match session으로 묶이지 않음.
- 후보가 없으면 해당 scheduler tick에서 매칭 성사 없이 종료됨.

구현 결과는 다음과 같음.

- `MatchPairingServiceTest.sameUserIdOnlyCandidatesDoNotMatch`를 추가함.
- queue snapshot에 `userA(userId=1)`와 `sameUser(userId=1)`만 배치함.
- 동일 userId 후보만 있을 때 `MatchQueueStore.atomicPairRemove`와 `MatchFoundService.process`가 호출되지 않음을 검증함.
- 매칭 성사가 없으므로 `recordPairsPerScan(0)`이 기록되는지 검증함.

### 7. 기존 매칭 정책 회귀 확인

- [ ] FIFO 정렬 후 오래 기다린 유저부터 후보를 탐색하는 기존 테스트 통과 확인
- [ ] tier range 단계별 테스트 통과 확인
- [ ] Apex/placement 매칭 정책 테스트 통과 확인
- [ ] `atomicPairRemove` 실패 시 다음 후보 탐색 테스트 통과 확인
- [ ] Issue 66 후처리 실패 scan loop 회귀 테스트 통과 확인

완료 기준은 다음과 같음.

- 동일 userId 방어 조건 추가 후에도 기존 매칭 정책이 유지됨.
- match found 후처리 복구 정책과 충돌하지 않음.

### 8. 문서와 checkpoint 갱신

- [ ] `docs/antigravity/backend/plan-checkpoint.md` Step 17과 Issue 68 항목을 구현 결과에 맞게 갱신
- [ ] 이 문서의 task 체크 상태와 구현 결과를 반영
- [ ] 동일 IP 기반 어뷰징 방지는 이번 이슈 범위에서 제외함을 유지

완료 기준은 다음과 같음.

- issue 문서와 checkpoint 문서의 제목, 범위, task가 일치함.
- Issue 66 문서의 후속 범위 설명과 충돌하지 않음.

### 9. 검증

- [ ] `./gradlew :smite-matching:test --tests '*MatchPairingServiceTest'` 실행
- [ ] `./gradlew :smite-matching:test` 실행
- [ ] 필요 시 전체 `./gradlew test` 실행
- [ ] 신규 self matching 방지 테스트가 통과하는지 확인
- [ ] 기존 pairing, match found 후처리 회귀 테스트가 통과하는지 확인

완료 기준은 다음과 같음.

- 신규 테스트와 기존 smite-matching 테스트가 통과함.
- 문서 변경은 `git diff --check`를 통과함.

## 변경 이력

| 날짜 | 변경 내용 |
|------|-----------|
| 2026-05-29 | Issue 68 셀프 매칭 방지 정책, 구현 흐름, task 초안 작성 |
