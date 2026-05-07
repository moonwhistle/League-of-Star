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

- [ ] `MatchSession`에 수락 상태 필드 추가
  - `boolean userAAccepted`
  - `boolean userBAccepted`
- [ ] 생성 메서드 수정
  - `MatchSession.create()`는 기본값 `false, false`로 생성
- [ ] 편의 메서드 검토
  - `isParticipant(Long userId)`
  - `isAcceptedByBoth()`
  - `isUserA(Long userId)`
  - `isUserB(Long userId)`
- [ ] 기존 테스트 영향 확인
  - `RedisMatchSessionStoreTest`
  - `MatchFoundServiceTest`

### 3. API 경로 정의

- [ ] `MatchPath`에 수락/거절 경로 추가
  - `/{matchId}/accept`
  - `/{matchId}/reject`
- [ ] path variable 이름 상수화 여부 검토
  - `matchId`
- [ ] API endpoint 정책 확정
  - `POST /api/v1/match/{matchId}/accept`
  - `POST /api/v1/match/{matchId}/reject`

### 4. API 모듈 Controller 구현

- [ ] `MatchController`에 수락 API 추가
  - `@PostMapping(MatchPath.ACCEPT)`
  - `@AuthUser Long userId`
  - `@PathVariable String matchId`
- [ ] `MatchController`에 거절 API 추가
  - `@PostMapping(MatchPath.REJECT)`
  - `@AuthUser Long userId`
  - `@PathVariable String matchId`
- [ ] Controller는 인증 유저와 matchId만 받고 서비스에 위임
- [ ] 응답은 우선 `200 OK` 또는 `204 No Content` 중 기존 스타일에 맞춰 결정
  - 현재 `join`, `leave`는 `200 OK` 사용
  - 이번 API도 일관성을 위해 `200 OK` 우선 검토

### 5. API 모듈 Service 분리

- [ ] `MatchQueueService`에 수락/거절을 넣을지 별도 서비스로 분리할지 결정
  - 현재 `MatchQueueService`는 join/leave 대기열 책임
  - 수락/거절은 매칭 세션 응답 책임
- [ ] 별도 `MatchResponseService` 추가 검토
  - 위치: `smite-api/src/main/java/com/sang/smite/match/service`
  - 역할: API layer에서 인증 유저 요청을 matching module로 위임
- [ ] API layer는 JPA rank 조회를 하지 않음
  - 수락/거절은 `matchId`, `userId`만 필요
- [ ] matching module의 수락/거절 서비스 호출

### 6. Matching 모듈 서비스 설계

- [ ] `MatchAcceptanceService` 또는 `MatchResponseService` 추가
  - 위치: `smite-matching/src/main/java/com/sang/smite/matching/service`
- [ ] 메서드 정의
  - `accept(String matchId, Long userId)`
  - `reject(String matchId, Long userId)`
- [ ] 책임 정의
  - 매칭 세션 조회
  - 참여자 검증
  - 세션 상태 검증
  - 수락/거절 상태 변경
  - 유저 상태 변경/정리
- [ ] `matchId` 기준 Redis lock 적용 위치 정의
  - `accept(matchId, userId)` 전체 처리 구간
  - `reject(matchId, userId)` 전체 처리 구간
  - timeout 처리 메서드가 생길 경우 동일하게 적용
- [ ] timeout 처리 메서드 정의 검토
  - `timeout(String matchId)`
  - 또는 accept/reject 요청 시 세션 만료를 감지해 timeout 정리
- [ ] `MatchService`와 책임 분리
  - `MatchService`: join/leave
  - `MatchFoundService`: 매칭 성사 후 세션 생성 및 알림 이벤트 발행
  - 새 서비스: 성사된 매칭에 대한 유저 응답 처리

### 7. MatchSessionStore 기능 확장

- [ ] `MatchSessionStore` 인터페이스에 응답 상태 저장 메서드 추가 검토
  - `accept(matchId, userId)`
  - `reject(matchId, userId)`
  - `timeout(matchId)`
  - 또는 더 명시적인 결과 타입 반환
- [ ] 단순 `findById -> save` 방식은 동시 요청에서 위험하므로 지양
- [ ] `matchId` 기준 lock이 서비스에서 상태 변경 전체를 감싸므로 store는 Hash 저장 책임에 집중
- [ ] store 메서드의 책임 분리 검토
  - 세션 조회
  - 특정 유저 수락 flag 변경
  - 세션 status 변경
  - 세션 삭제 또는 TTL 조정
- [ ] 반환 결과 타입 정의 검토
  - 세션 없음
  - 참여자 아님
  - 이미 종료됨
  - 수락 저장됨
  - 양쪽 수락 완료
  - 거절 완료

### 8. Redis lock 기반 동시성 처리 설계

- [ ] 기존 Redis 분산락 어노테이션 구조 확인
  - lock key를 `matchId` 기준으로 생성할 수 있는지 확인
  - wait time, lease time, watchdog 사용 여부 확인
  - AOP 적용 시 트랜잭션/부가 로직이 불필요하게 섞이지 않는지 확인
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
