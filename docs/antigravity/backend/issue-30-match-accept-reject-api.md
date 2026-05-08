# Issue-30: 매칭 수락/거절 API 구현

## 📌 Feature Description

매칭 성사 후 생성된 `match:session:{matchId}`를 기준으로 유저가 제한 시간 안에 매칭을 수락하거나 거절할 수 있는 API를 구현합니다.

Issue-28에서 SSE `match_found` 알림은 이미 구현했습니다. 이번 이슈는 알림을 받은 클라이언트가 실제 선택을 서버에 전달하는 HTTP API를 다룹니다.

정책은 다음과 같습니다.

- `match_found` 알림은 SSE로 전달합니다.
- 유저의 `accept`, `reject` 선택은 HTTP API로 처리합니다.
- 요청 유저는 해당 `matchId`의 참여자여야 합니다.
- 양쪽 유저가 모두 수락하면 매칭 세션은 완료 상태로 전환합니다.
- 한 명이라도 거절하면 매칭 세션은 거절 상태로 전환하고 양쪽 유저 상태를 정리합니다.
- 수락 제한 시간이 지나면 timeout으로 처리합니다. timeout은 reject와 같은 종료/정리 흐름을 사용하되, 상태값은 `TIMEOUT`으로 분리합니다.
- 동시 수락/거절 요청에서도 세션 상태가 꼬이지 않도록 `matchId` 기준 Redis 분산락을 사용합니다.

### 이번 이슈 핵심 결정

```text
상태 저장:
  MatchSession에 userAAccepted, userBAccepted 추가

동시성 제어:
  accept/reject/timeout 처리는 matchId 기준 Redis lock으로 직렬화

timeout 정책:
  reject와 동일하게 매칭 실패 종료 흐름을 타지만,
  유저가 직접 거절한 것은 DECLINED,
  시간 초과는 TIMEOUT으로 상태를 분리
```

## 📚 Tasks

### 1. 현재 매칭 세션 구조 확인

- [x] `MatchSession` 현재 필드 확인
  - `matchId`
  - `userA`
  - `userB`
  - `status`
  - `createdAt`
- [x] 현재 Redis Hash 구조 확인
  - key: `match:session:{matchId}`
  - fields: `matchId`, `userA`, `userB`, `status`, `createdAt`
- [x] 유저 상태 저장소 확인
  - key: `match:user:status:{userId}`
  - status: `MATCHING`, `FOUND`, `ACCEPTED`, `DECLINED`, `TIMEOUT`, `IN_GAME`
- [x] 이번 이슈에서 추가로 필요한 세션 필드 정의
  - `userAAccepted`
  - `userBAccepted`
  - 필요 시 `completedAt`

#### 확인 결과

- 현재 `MatchSession`은 수락 대기 세션을 표현하지만, 유저별 수락 여부는 저장하지 않습니다.
  - 현재 필드: `matchId`, `userA`, `userB`, `status`, `createdAt`
  - 생성 시 `status=FOUND`로 저장됩니다.
- Redis 세션은 Hash 구조입니다.
  - key: `match:session:{matchId}`
  - fields: `matchId`, `userA`, `userB`, `status`, `createdAt`
  - TTL은 `MatchFoundService`에서 `12초`로 저장합니다.
- 클라이언트 수락/거절 모달 정책은 10초이고, Redis 세션 TTL은 네트워크/스케줄링 여유를 두어 12초로 설정되어 있습니다.
- 유저별 매칭 상태는 별도 Redis bucket에 저장됩니다.
  - key prefix: `match:status:`
  - 실제 key: `match:status:{userId}`
  - `FOUND` 상태 TTL은 `MatchingConstants.STATUS_TTL_SECONDS = 1800초`입니다.
- 매칭 성사 시 현재 흐름은 다음과 같습니다.
  - `MatchEngineService`가 두 유저를 Redis 대기열에서 원자 제거
  - `MatchFoundService`가 두 유저 상태를 `FOUND`로 변경
  - `MatchFoundService`가 `match:session:{matchId}` 저장
  - `MatchFoundEvent` 발행
  - SSE/PubSub 경로로 `match_found` 알림 전송

#### 수락/거절 구현 시 필요한 보강

- 양쪽 유저의 수락 여부를 세션에 저장해야 합니다.
  - `userAAccepted`
  - `userBAccepted`
- 현재 `MatchSession`에는 참여자 검증 메서드가 없어 서비스에서 직접 비교해야 합니다.
  - `isParticipant(userId)`
  - `isUserA(userId)`
  - `isUserB(userId)`
  - `isAcceptedByBoth()`
- 현재 `RedisMatchSessionStore`는 `save/find/delete`만 지원합니다.
  - 수락/거절은 동시 요청 가능성이 있으므로 단순 `findById -> save` 방식은 위험합니다.
  - 다음 작업에서 `matchId` 기준 Redis 분산락을 적용한 응답 처리 흐름을 설계해야 합니다.
- 세션 TTL은 12초지만 유저 status TTL은 30분입니다.
  - 세션이 만료된 뒤 유저 상태가 `FOUND`로 남는 상황을 어떻게 정리할지 정책이 필요합니다.
  - timeout은 reject와 같은 정리 흐름을 사용하되 상태는 `TIMEOUT`으로 분리합니다.

### 2. 도메인 모델 확장

- [x] `MatchSession`에 수락 상태 필드 추가
  - `boolean userAAccepted`
  - `boolean userBAccepted`
- [x] 생성 메서드 수정
  - `MatchSession.create()`는 기본값 `false, false`로 생성
- [x] 편의 메서드 검토
  - `isParticipant(Long userId)`
  - `isAcceptedByBoth()`
  - `isUserA(Long userId)`
  - `isUserB(Long userId)`
- [x] 기존 테스트 영향 확인
  - `RedisMatchSessionStoreTest`
  - `MatchFoundServiceTest`

#### 구현 결과

- `MatchSession`에 유저별 수락 여부를 추가했습니다.
  - `userAAccepted`
  - `userBAccepted`
- `MatchSession.create()`는 매칭 성사 직후 수락 대기 상태를 만들기 때문에 두 수락 필드를 모두 `false`로 초기화합니다.
- 참여자/수락 완료 판단 메서드를 추가했습니다.
  - `isParticipant(Long userId)`
  - `isUserA(Long userId)`
  - `isUserB(Long userId)`
  - `isAcceptedByBoth()`
- `RedisMatchSessionStore` 저장/복원 필드를 확장했습니다.
  - `userAAccepted`
  - `userBAccepted`
- 테스트를 보강했습니다.
  - `MatchSessionTest`: 생성 기본값, 참여자 검증, 양쪽 수락 여부 검증
  - `RedisMatchSessionStoreTest`: 수락 필드 Redis Hash 저장/복원 검증
  - `MatchFoundServiceTest`: 매칭 성사 직후 수락 필드 기본값 검증

### 3. API 경로 정의

- [x] `MatchPath`에 수락/거절 경로 추가
  - `/{matchId}/accept`
  - `/{matchId}/reject`
- [x] path variable 이름 상수화 여부 검토
  - `matchId`
- [x] API endpoint 정책 확정
  - `POST /api/v1/match/{matchId}/accept`
  - `POST /api/v1/match/{matchId}/reject`

#### 구현 결과

- `MatchPath`에 수락/거절 API path 상수를 추가했습니다.
  - `ACCEPT = "/{matchId}/accept"`
  - `REJECT = "/{matchId}/reject"`
- path variable 이름도 상수화했습니다.
  - `MATCH_ID = "matchId"`
- 최종 API 경로는 다음과 같습니다.
  - `POST /api/v1/match/{matchId}/accept`
  - `POST /api/v1/match/{matchId}/reject`

### 4. API 모듈 Controller 구현

- [x] `MatchController`에 수락 API 추가
  - `@PostMapping(MatchPath.ACCEPT)`
  - `@AuthUser Long userId`
  - `@PathVariable String matchId`
- [x] `MatchController`에 거절 API 추가
  - `@PostMapping(MatchPath.REJECT)`
  - `@AuthUser Long userId`
  - `@PathVariable String matchId`
- [x] Controller는 인증 유저와 matchId만 받고 서비스에 위임
- [x] 응답은 우선 `200 OK` 또는 `204 No Content` 중 기존 스타일에 맞춰 결정
  - 현재 `join`, `leave`는 `200 OK` 사용
  - 이번 API도 일관성을 위해 `200 OK` 우선 검토

#### 구현 결과

- `MatchController`에 수락/거절 endpoint를 추가했습니다.
  - `POST /api/v1/match/{matchId}/accept`
  - `POST /api/v1/match/{matchId}/reject`
- 컨트롤러는 `@AuthUser Long userId`와 `@PathVariable String matchId`만 받아 API 서비스에 위임합니다.
- 기존 `join`, `leave`와 일관성을 맞춰 성공 응답은 `200 OK`로 결정했습니다.
- 컴파일을 위해 API 레이어 위임 서비스인 `MatchResponseService`를 추가했습니다.
  - 실제 세션 상태 변경, `matchId` 기준 Redis lock, timeout 처리는 후속 task에서 구현합니다.
  - 현재 서비스 메서드는 비즈니스 로직 미구현 상태를 명확히 드러내도록 `UnsupportedOperationException`을 던집니다.
- `MatchControllerTest`를 추가해 accept/reject 요청이 서비스로 위임되는지 검증했습니다.

### 5. API 모듈 Service 분리

- [x] `MatchQueueService`에 수락/거절을 넣을지 별도 서비스로 분리할지 결정
  - 현재 `MatchQueueService`는 join/leave 대기열 책임
  - 수락/거절은 매칭 세션 응답 책임
- [x] 별도 `MatchResponseService` 추가 검토
  - 위치: `smite-api/src/main/java/com/sang/smite/match/service`
  - 역할: API layer에서 인증 유저 요청을 matching module로 위임
- [x] API layer는 JPA rank 조회를 하지 않음
  - 수락/거절은 `matchId`, `userId`만 필요
- [x] matching module의 수락/거절 서비스 호출

#### 구현 결과

- 수락/거절 요청은 `MatchQueueService`에 넣지 않고 별도 `MatchResponseService`로 분리했습니다.
  - `MatchQueueService`: join/leave 대기열 책임
  - `MatchResponseService`: 매칭 성사 후 accept/reject 응답 위임 책임
- API 모듈 `MatchResponseService`는 JPA rank 조회를 하지 않습니다.
  - 수락/거절에는 `matchId`, `userId`만 필요합니다.
- matching 모듈에 `MatchResponseCommandService`를 추가했습니다.
  - 위치: `smite-matching/src/main/java/com/sang/smite/matching/service`
  - API 모듈은 이 서비스를 호출해 수락/거절 처리를 위임합니다.
  - 실제 세션 상태 변경, `matchId` 기준 Redis lock, timeout 정책은 다음 task에서 구현합니다.
- `MatchResponseServiceTest`를 추가해 API 서비스가 matching 모듈 서비스로 accept/reject를 위임하는지 검증했습니다.

### 6. Matching 모듈 서비스 설계

- [x] `MatchAcceptanceService` 또는 `MatchResponseService` 추가
  - 위치: `smite-matching/src/main/java/com/sang/smite/matching/service`
- [x] 메서드 정의
  - `accept(String matchId, Long userId)`
  - `reject(String matchId, Long userId)`
- [x] 책임 정의
  - 매칭 세션 조회
  - 참여자 검증
  - 세션 상태 검증
  - 수락/거절 상태 변경
  - 유저 상태 변경/정리
- [x] `matchId` 기준 Redis lock 적용 위치 정의
  - `accept(matchId, userId)` 전체 처리 구간
  - `reject(matchId, userId)` 전체 처리 구간
  - timeout 처리 메서드가 생길 경우 동일하게 적용
- [x] timeout 처리 메서드 정의 검토
  - `timeout(String matchId)`
  - 또는 accept/reject 요청 시 세션 만료를 감지해 timeout 정리
- [x] `MatchService`와 책임 분리
  - `MatchService`: join/leave
  - `MatchFoundService`: 매칭 성사 후 세션 생성 및 알림 이벤트 발행
  - 새 서비스: 성사된 매칭에 대한 유저 응답 처리

#### 설계 결과

- matching 모듈의 수락/거절 서비스는 기존 `MatchResponseCommandService`를 사용합니다.
  - API 모듈에 이미 같은 이름의 `MatchResponseService`가 있으므로, matching 모듈까지 같은 이름으로 만들면 책임 경계가 흐려질 수 있습니다.
  - `Command`를 붙여서 "세션 상태를 변경하는 명령 서비스"라는 의도를 명확히 둡니다.
- 공개 메서드는 다음 2개를 우선 구현합니다.
  - `accept(String matchId, Long userId)`
  - `reject(String matchId, Long userId)`
- timeout은 별도 메서드로 확장 가능하게 설계합니다.
  - `timeout(String matchId)`
  - 다만 현재 클라이언트 수락/거절 API 구현이 우선이므로, timeout 스케줄러/이벤트 처리는 후속 작업에서 붙일 수 있게 경계만 열어둡니다.

#### 서비스 책임

`MatchResponseCommandService`는 다음 책임만 가집니다.

1. `matchId` 기준 Redis lock 획득
2. `MatchSessionStore`에서 세션 조회
3. 요청 유저가 `userA` 또는 `userB`인지 검증
4. 현재 세션 상태가 응답 가능한 상태인지 검증
5. accept/reject에 따라 세션 상태 변경
6. `MatchUserStatusStore`에 유저 상태 반영 또는 정리

다음 책임은 포함하지 않습니다.

- `joinQueue`, `leaveQueue`
  - 기존 `MatchService` 책임입니다.
- 매칭 성사 세션 생성 및 `match_found` 알림 발행
  - 기존 `MatchFoundService` 책임입니다.
- 수락/거절 결과를 상대방에게 SSE로 다시 알려주는 기능
  - 후속 이슈로 분리합니다.
- 게임 세션 생성 및 `IN_GAME` 전환
  - 게임 시작 플로우 이슈에서 처리합니다.

#### Redis lock 적용 방식

- lock key는 `matchId` 단위로 잡습니다.
  - 예: `match:session:lock:{matchId}`
- 같은 매칭 세션에 대한 `accept`, `reject`, `timeout`만 직렬화합니다.
- 서로 다른 `matchId`는 동시에 처리될 수 있어야 하므로 전역 lock은 사용하지 않습니다.
- 현재 `@DistributedLock`은 SpEL 기반 key 생성은 가능하지만, AOP 내부에서 `AopForTransaction`을 항상 거칩니다.
  - 이번 수락/거절 처리는 Redis Hash와 Redis 상태 저장소만 다루는 matching 모듈 명령입니다.
  - JPA 트랜잭션 경계가 필요하지 않으므로 `RLock.tryLock()`을 서비스 내부에서 직접 사용하는 방식이 더 적합합니다.
  - 매칭 엔진에서 사용한 방식과 동일하게 Redis-only 작업은 명시적 lock으로 처리합니다.

#### 상태 전이 규칙

수락 처리:

```text
FOUND
  -> userA만 수락: FOUND + userAAccepted=true
  -> userB만 수락: FOUND + userBAccepted=true
  -> 둘 다 수락: ACCEPTED + userAAccepted=true + userBAccepted=true
```

- 요청 유저 상태는 `ACCEPTED`로 변경합니다.
- 한 명만 수락한 경우 세션 status는 아직 `FOUND`로 유지합니다.
  - 이유: 매칭은 "양쪽 모두 수락"해야 완료입니다.
- 양쪽 모두 수락하면 세션 status를 `ACCEPTED`로 변경합니다.
- `IN_GAME` 전환은 이번 이슈 범위가 아닙니다.

거절 처리:

```text
FOUND -> DECLINED
ACCEPTED -> 거절 불가
DECLINED/TIMEOUT -> 이미 종료된 세션
```

- 한 명이라도 거절하면 세션 status는 `DECLINED`로 변경합니다.
- 양쪽 유저의 매칭 상태는 제거합니다.
  - 이유: 다시 매칭 버튼을 누를 수 있어야 합니다.
- 분석 목적의 거절 상태 보관은 Redis 세션 status로 확인하고, 유저 status는 재매칭 가능성을 우선합니다.

timeout 처리:

```text
FOUND -> TIMEOUT
ACCEPTED -> timeout 불가
DECLINED/TIMEOUT -> 이미 종료된 세션
```

- timeout은 reject와 같은 정리 흐름을 사용합니다.
- 단, 사용자가 직접 거절한 것과 구분하기 위해 세션 status는 `TIMEOUT`으로 분리합니다.
- 세션 TTL이 먼저 만료되어 세션이 없으면, API 요청에서는 만료/없음 에러를 반환합니다.
- 남아 있는 유저 status 보정은 timeout 처리 작업에서 별도로 다룹니다.

#### 중복 요청 정책

- 같은 유저가 같은 세션에 accept를 두 번 보내는 경우는 멱등 처리 후보입니다.
  - 이미 해당 유저의 accepted flag가 `true`이면 성공으로 간주해도 상태가 꼬이지 않습니다.
  - V1에서는 클라이언트 재시도 안정성을 위해 중복 accept는 성공 처리하는 방향이 적합합니다.
- 이미 `ACCEPTED`, `DECLINED`, `TIMEOUT`으로 종료된 세션에 반대 응답이 들어오면 conflict로 처리합니다.
- lock 획득 실패는 같은 `matchId`에 대한 요청이 처리 중이라는 뜻이므로 conflict 또는 retryable error로 처리합니다.

### 7. Redis-only 분산락 어노테이션 구현

- [x] 기존 `@DistributedLock` 역할 재정의
  - 위치: `smite-infra-redis/src/main/java/com/sang/smite/redis/lock/annotation/DistributedLock.java`
  - 역할: Redis lock + `AopForTransaction` 기반 `REQUIRES_NEW` 트랜잭션
  - 사용처: DB 트랜잭션 정합성이 필요한 작업
- [x] 신규 `@DistributedRedisLock` 추가
  - 위치: `smite-infra-redis/src/main/java/com/sang/smite/redis/lock/annotation/DistributedRedisLock.java`
  - 역할: Redis lock만 적용
  - 트랜잭션 AOP를 태우지 않음
  - 사용처: Redis-only 작업
    - 매칭 수락/거절
    - timeout 처리
    - 향후 Redis 상태 기반 동시성 제어
- [x] 신규 AOP 추가
  - 위치: `smite-infra-redis/src/main/java/com/sang/smite/redis/lock/aop/DistributedRedisLockAop.java`
  - 기존 `CustomSpringELParser` 재사용
  - 기존 `LockConstants.REDISSON_LOCK_PREFIX` 재사용
  - `RedissonClient.getLock(key)` 사용
- [x] 어노테이션 속성 정의
  - `key`
  - `timeUnit`
  - `waitTime`
  - `leaseTime`
- [x] watchdog 정책 지원
  - `leaseTime > 0`: `tryLock(waitTime, leaseTime, timeUnit)` 사용
  - `leaseTime < 0`: `tryLock(waitTime, timeUnit)` 사용
  - `leaseTime < 0`이면 Redisson watchdog이 lock을 자동 연장하도록 설계
- [x] lock 획득 실패 정책 정의
  - 기존 `@DistributedLock`처럼 `false`를 반환하지 않음
  - 수락/거절 API는 반환 타입이 `void`이므로 `false` 반환 방식이 맞지 않음
  - lock 획득 실패 시 명확한 예외를 던져 API 계층에서 에러 응답으로 변환
- [x] 예외 타입 정의
  - 기존 `ApiException`/`MatchingException` 흐름을 확인해 프로젝트 예외 정책에 맞춤
  - Redis lock 공통 예외를 infra-redis에 둘지, matching 도메인 예외로 변환할지 결정
- [x] 테스트 작성
  - SpEL key 생성 검증
  - lock 획득 시 원본 메서드 실행 검증
  - lock 획득 실패 시 예외 발생 검증
  - `leaseTime < 0` watchdog 분기 검증

#### 작업 이유

현재 `@DistributedLock`은 Redis lock을 잡은 뒤 항상 `AopForTransaction.proceed()`를 호출합니다.

```text
@DistributedLock
  -> Redis lock 획득
  -> REQUIRES_NEW 트랜잭션 시작
  -> 비즈니스 로직 실행
  -> 트랜잭션 종료
  -> lock 해제
```

이 구조는 DB 정합성이 필요한 작업에는 적합합니다. 하지만 매칭 수락/거절은 Redis Hash 세션과 Redis 유저 상태만 변경하는 Redis-only 작업입니다. 이 작업에 JPA 트랜잭션을 강제로 붙이면 의미 없는 트랜잭션 경계가 생기고, matching 모듈의 책임도 흐려집니다.

따라서 락을 다음처럼 분리합니다.

```text
@DistributedLock
  DB 트랜잭션이 필요한 분산락

@DistributedRedisLock
  Redis-only 작업에 사용하는 분산락
```

#### 패키지 구조

```text
smite-infra-redis
└── src/main/java/com/sang/smite/redis
    ├── common/constant
    │   └── LockConstants.java
    └── lock
        ├── annotation
        │   ├── DistributedLock.java
        │   └── DistributedRedisLock.java
        ├── aop
        │   ├── AopForTransaction.java
        │   ├── DistributedLockAop.java
        │   └── DistributedRedisLockAop.java
        └── parser
            └── CustomSpringELParser.java
```

#### 수락/거절 적용 예시

```java
@DistributedRedisLock(
        key = "'match:session:lock:' + #matchId",
        waitTime = 500,
        leaseTime = -1
)
public void accept(String matchId, Long userId) {
    // 세션 조회
    // 참여자 검증
    // 수락 상태 저장
    // 양쪽 수락 시 ACCEPTED 전환
}
```

`leaseTime = -1`은 watchdog 사용 의도를 나타냅니다. 수락/거절은 짧은 작업이지만, Redis 지연이나 GC pause 같은 상황에서 고정 lease time 때문에 lock이 먼저 풀리는 위험을 피할 수 있습니다.

#### 구현 결과

- `DistributedRedisLock` 어노테이션을 추가했습니다.
  - 기본 `waitTime`: `500ms`
  - 기본 `leaseTime`: `-1`
  - `leaseTime < 0`이면 Redisson watchdog을 사용합니다.
- `DistributedRedisLockAop`를 추가했습니다.
  - 기존 `CustomSpringELParser`로 SpEL key를 파싱합니다.
  - 기존 `LockConstants.REDISSON_LOCK_PREFIX`를 사용해 lock key prefix를 통일합니다.
  - 기존 `@DistributedLock`과 달리 `AopForTransaction`을 호출하지 않습니다.
- 기존 `DistributedLockAop`도 락 계열 정책에 맞춰 정리했습니다.
  - lock 획득 실패 시 `false`를 반환하지 않고 `RedisLockAcquisitionException`을 던집니다.
  - `leaseTime < 0`이면 watchdog을 사용할 수 있게 했습니다.
  - `InterruptedException` 변환 범위를 `tryLock()` 구간으로 좁혀 원본 메서드 예외를 보존합니다.
- lock 획득 실패 시 `false`를 반환하지 않고 `RedisLockAcquisitionException`을 던지도록 했습니다.
  - 수락/거절 API는 `void` 반환 흐름이므로 `false` 반환 방식은 맞지 않습니다.
- 테스트를 추가했습니다.
  - watchdog 분기: `tryLock(waitTime, timeUnit)`
  - 고정 lease time 분기: `tryLock(waitTime, leaseTime, timeUnit)`
  - lock 획득 실패 시 원본 메서드 미실행

검증:

```bash
./gradlew :smite-infra-redis:test --tests 'com.sang.smite.redis.lock.aop.*LockAopTest'
```

결과: `BUILD SUCCESSFUL`

### 8. Redis lock 기반 동시성 처리 설계

- [ ] 신규 `@DistributedRedisLock` 적용 위치 확정
  - `MatchResponseCommandService.accept(matchId, userId)`
  - `MatchResponseCommandService.reject(matchId, userId)`
  - timeout 처리 메서드가 추가되면 동일하게 적용
- [ ] lock key 정책 정의
  - 예: `match:session:lock:{matchId}`
  - 같은 매칭 세션의 accept/reject/timeout 요청만 직렬화
  - 서로 다른 matchId는 병렬 처리 가능
- [ ] 수락 처리 흐름 설계
  - `matchId` lock 획득
  - 세션 key 존재 여부 확인
  - 요청 유저가 `userA` 또는 `userB`인지 확인
  - 세션 status가 `FOUND`인지 확인
  - 해당 유저 accepted field를 `true`로 변경
  - 양쪽 accepted가 모두 `true`면 status를 `ACCEPTED`로 변경
  - 유저 상태를 `ACCEPTED`로 변경
  - lock 해제
- [ ] 거절 처리 흐름 설계
  - `matchId` lock 획득
  - 세션 key 존재 여부 확인
  - 요청 유저가 참여자인지 확인
  - 이미 완료/거절/timeout된 세션인지 확인
  - status를 `DECLINED`로 변경
  - 양쪽 유저 상태 정리
  - lock 해제
- [ ] timeout 처리 흐름 설계
  - `matchId` lock 획득
  - 세션이 남아 있으면 status를 `TIMEOUT`으로 변경
  - 양쪽 유저 상태 정리
  - 세션이 이미 TTL로 삭제된 경우 유저 상태 보정 가능 여부 검토
  - lock 해제
- [ ] Redis Hash field 상수화
  - `userAAccepted`
  - `userBAccepted`
- [ ] `MatchSessionStore` 기능 확장 검토
  - `MatchSessionStore`는 Redis Hash 저장/조회 책임에 집중
  - 동시성은 `@DistributedRedisLock`이 서비스 메서드 전체를 감싸서 보장
  - 단순 `findById -> save`는 lock 안에서만 사용
  - 필요 시 `save` 외에 상태 변경 의도가 드러나는 메서드 추가
- [ ] lock 기반 V1로 충분한지 검증
  - 동시 accept
  - accept/reject 충돌
  - 중복 accept
  - timeout 근처 요청

### 9. 유저 매칭 상태 처리 정책

- [ ] 수락 시 요청 유저 상태 변경
  - `FOUND -> ACCEPTED`
- [ ] 양쪽 수락 완료 시 양쪽 유저 상태 변경
  - 이번 이슈에서는 게임 생성/입장 흐름 전 단계이므로 `ACCEPTED` 유지 우선
  - `IN_GAME` 전환은 게임 세션 생성 이슈에서 처리
- [ ] 거절 시 처리
  - 세션 status는 `DECLINED`
  - 양쪽 유저 상태는 재매칭 가능하도록 제거 우선
  - 분석 목적의 `DECLINED` 유지가 필요하면 짧은 TTL 정책을 별도 검토
- [ ] timeout 시 처리
  - reject와 동일한 종료/정리 흐름 사용
  - 단, 세션 status는 `TIMEOUT`으로 분리
  - 양쪽 유저 상태는 재매칭 가능하도록 제거 우선
- [ ] 세션 만료 후 유저 상태 보정 정책 검토
  - Redis 세션 TTL이 먼저 만료되면 `FOUND` 상태가 남을 수 있음
  - accept/reject 요청 시 세션 없음이면 현재 유저 상태를 확인해 보정할지 결정

### 10. 에러 코드 정의

- [ ] `MatchingErrorCode`에 수락/거절 관련 코드 추가
  - `MATCH_SESSION_NOT_FOUND`
  - `MATCH_SESSION_EXPIRED`
  - `MATCH_SESSION_NOT_PARTICIPANT`
  - `MATCH_SESSION_ALREADY_COMPLETED`
  - `MATCH_SESSION_ALREADY_DECLINED`
  - `MATCH_SESSION_TIMEOUT`
  - `MATCH_ACCEPT_CONFLICT`
  - `MATCH_REJECT_CONFLICT`
  - `MATCH_RESPONSE_LOCK_FAILED`
- [ ] HTTP status 정책 결정
  - 세션 없음/만료: `404` 또는 `410`
  - 참여자 아님: `403`
  - 이미 완료/거절: `409`
  - lock 획득 실패: `409` 또는 `429`
  - 내부 Redis 처리 실패: `500`
- [ ] 기존 `MatchingException` 사용

### 11. 이벤트 후속 확장 지점 정리

- [ ] 이번 이슈에서 클라이언트에게 추가 SSE 이벤트를 보낼지 결정
  - 예: `match_accept`, `match_reject`, `match_completed`, `match_timeout`
- [ ] 범위 초과라면 후속 이슈로 분리
- [ ] 최소한 내부 이벤트 발행 지점은 열어둘지 검토
  - `MatchAcceptedEvent`
  - `MatchRejectedEvent`
  - `MatchCompletedEvent`
- [ ] 현재 이슈는 API 상태 변경까지만 하고, 실시간 상태 동기화 이벤트는 후속 이슈로 남기는 방향 우선

### 12. API 문서화 및 RestDocs

- [ ] 수락 API RestDocs 작성
  - path parameter: `matchId`
  - 인증 필요
  - 성공 응답
  - 주요 실패 응답
- [ ] 거절 API RestDocs 작성
  - path parameter: `matchId`
  - 인증 필요
  - 성공 응답
  - 주요 실패 응답
- [ ] OpenAPI 문서 생성 흐름 확인

### 13. 단위 테스트 작성

- [ ] Matching service 단위 테스트
  - 수락 성공
  - 양쪽 수락 완료
  - 거절 성공
  - timeout 성공
  - 세션 없음
  - 참여자 아님
  - 이미 종료된 세션
  - lock 획득 실패
- [ ] API service 단위 테스트
  - controller/service 위임 검증
- [ ] Controller 테스트
  - 인증 유저 기준 accept 요청
  - 인증 유저 기준 reject 요청
  - path variable 전달 검증

### 14. Redis 통합 테스트 작성

- [ ] `RedisMatchSessionStoreTest` 보강
  - 수락 field 저장/복원
  - 한 명 수락
  - 양쪽 수락 완료
  - 거절 처리
  - timeout 처리
  - 없는 세션 처리
  - 참여자 아닌 유저 처리
  - 이미 종료된 세션 처리
- [ ] 동시성 테스트
  - userA/userB 동시 accept
  - accept와 reject가 거의 동시에 들어오는 경우
  - 같은 유저가 중복 accept 하는 경우
  - timeout과 accept/reject가 거의 동시에 들어오는 경우
- [ ] Redis TTL 유지 여부 검증

### 15. 관측 지표 검토

- [ ] 수락/거절 API 호출 수
- [ ] 수락 성공/실패 수
- [ ] 거절 성공/실패 수
- [ ] 양쪽 수락 완료 수
- [ ] 세션 만료/없음 실패 수
- [ ] timeout 처리 수
- [ ] 동시성 conflict 수
- [ ] lock 획득 실패 수
- [ ] 이번 이슈에서 Micrometer를 바로 추가할지, 부하 테스트 단계에서 추가할지 결정

### 16. 부하 테스트 계획

- [ ] SSE `match_found` 수신 이후 accept API를 호출하는 시나리오 작성 여부 검토
- [ ] 1,000 / 5,000 / 10,000명 기준 수락 API 부하 테스트 계획
- [ ] 양쪽 모두 수락하는 정상 시나리오
- [ ] 일부 유저 거절 시나리오
- [ ] timeout 이후 accept 요청 시나리오
- [ ] 10초 수락 제한 근처에서 accept/reject/timeout이 섞이는 시나리오
- [ ] 이번 이슈에서는 기능 테스트까지, 부하 테스트는 별도 task로 분리할지 결정

### 17. 최종 검증

- [ ] `:smite-core:test`
- [ ] `:smite-matching:test`
- [ ] `:smite-api:test`
- [ ] 필요 시 `./gradlew build`
- [ ] 기존 SSE `match_found` 흐름이 깨지지 않는지 확인
- [ ] join/leave 기존 API 회귀 확인

## 📝 Note

- 이번 이슈는 **수락/거절 API와 Redis 상태 변경**이 핵심입니다.
- `match_found` 알림 전송은 Issue-28에서 완료된 SSE/PubSub 구조를 그대로 사용합니다.
- 수락/거절 결과를 상대방에게 실시간으로 알려주는 SSE 이벤트는 후속 이슈로 분리하는 것을 우선합니다.
- 세션 상태 변경은 동시 요청 가능성이 있으므로 `findById -> save` 방식만 단독으로 사용하지 않습니다.
- V1 동시성 제어는 `matchId` 기준 Redis 분산락으로 처리합니다.
- timeout은 reject와 같은 종료/정리 흐름을 사용하지만, 상태값은 `TIMEOUT`으로 분리합니다.
- 최종 게임 생성/입장 처리는 별도 게임 플로우 이슈와 연결될 수 있으므로, 이번 이슈에서는 상태 전환 경계만 명확히 합니다.

## 📌 Related Issue

- Closes #30
