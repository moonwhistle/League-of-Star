# Issue 66. Match Found Post-processing Recovery

## 📌 Feature Description

Match found post-processing should recover matched users safely when session, timeout, status, or event steps fail after Redis queue removal.

매칭 엔진은 `atomicPairRemove`로 두 유저를 Redis queue에서 원자 제거한 뒤 `MatchFoundService.process`를 호출한다. 이슈 시작 시점의 `MatchFoundService`는 user status를 `FOUND`로 먼저 갱신하고, `MatchSession` 저장, timeout pending 등록, `MatchFoundEvent` 발행을 순서대로 수행했다. 이 중간 단계에서 예외가 발생하면 큐에서 제거된 유저가 session 없이 `FOUND`에 갇히거나, timeout 정산 경로 없이 queue에서도 사라진 상태가 될 수 있다.

이번 이슈는 `atomicPairRemove` 성공 이후의 match found 후처리를 보상 가능한 흐름으로 정리한다. 외부에 `FOUND` 상태를 노출하기 전에 응답 가능한 `MatchSession`과 timeout 정산 경로를 먼저 만들고, session을 만들 수 없는 실패는 두 유저를 기존 `MatchTicket.entryTime`과 `tierScore`로 queue에 복귀시킨다.

```mermaid
flowchart TD
    A[MatchEngineScheduler tick] --> B[MatchPairingService loadSortedTickets]
    B --> C[대기 시간과 tierScore 기준 후보 선택]
    C --> D{atomicPairRemove 성공}
    D -->|No| E[다음 후보 탐색]
    D -->|Yes| F[pairedUserIds 등록]
    F --> G[MatchFoundService.process]

    subgraph POST["match_found 후처리"]
        G --> H[MatchSession 생성 및 저장]
        H --> I[timeout pending 등록]
        I --> J[userA status FOUND 갱신]
        J --> K[userB status FOUND 갱신]
        K --> L[MatchFoundEvent 발행]
        L --> M[Redis Pub/Sub match_found fan-out]
        M --> N[SSE match_found 전송]
    end

    H -. 실패 .-> R1[두 유저 queue 복귀]
    I -. 실패 .-> R2[session 삭제 후 두 유저 queue 복귀]
    J -. 실패 .-> R3[timeout cleanup + session 삭제 + 두 유저 queue 복귀]
    K -. 실패 .-> R3
    L -. 실패 .-> R4[session/status/timeout 유지]

    R1 --> S1[status MATCHING 강제 복구]
    R2 --> S1
    R3 --> S1
    R4 --> T[10초 timeout 정산에 위임]
    S1 --> U[다음 scheduler tick에서 재매칭 가능]
```

핵심 정책은 다음과 같음.

- `FOUND` 상태를 유저에게 노출하기 전에 `MatchSession`과 timeout pending을 먼저 만든다.
- `MatchSession` 또는 timeout pending을 만들 수 없으면 해당 match found는 성립하지 않은 것으로 보고 두 유저를 queue에 복귀시킨다.
- queue 복귀 시 두 유저의 기존 `MatchTicket.entryTime`과 `tierScore`를 유지한다.
- queue 복귀가 확정된 보상 흐름에서는 `match:status:{userId}`를 `MATCHING`으로 강제 복구한다.
- userA 또는 userB 중 한 명의 `FOUND` 갱신만 성공한 경우에도 session을 폐기하고 두 유저 모두 queue에 복귀시킨다.
- `MatchFoundEvent` 발행 실패는 session, timeout, `FOUND` status를 유지하고 즉시 queue 복귀하지 않는다.
- 이벤트 발행 실패 후 응답이 없으면 기존 10초 timeout scheduler가 `PENDING + PENDING` 또는 실제 응답 조합을 최종 정산한다.
- `atomicPairRemove` 성공 후 paired 처리된 유저는 같은 scan cycle에서 다시 매칭하지 않고 다음 scheduler tick에서 다시 후보가 된다.
- 복구 작업은 Redis best-effort 보상으로 처리하고, outbox/saga 기반 보장형 복구는 이번 이슈 범위에 포함하지 않는다.
- 이번 이슈에서는 metric, Grafana dashboard를 추가하지 않는다.

### Current Implementation Analysis

이슈 시작 시점의 구현 상태는 다음과 같음.

- `MatchPairingService.processMatching`은 Redis queue snapshot을 읽고 `entryTime` 오름차순으로 정렬함.
- `MatchPairingService.confirmPair`는 후보 두 유저를 `MatchQueueStore.atomicPairRemove`로 queue에서 원자 제거함.
- `atomicPairRemove`가 성공하면 `completeMatchedPair`가 두 유저를 `pairedUserIds`에 등록하고 `MatchFoundService.process`를 호출함.
- `MatchPairingService.handleMatchedPair`는 `MatchFoundService.process` 예외를 catch하고 error log만 남긴 뒤 다음 후보 처리를 계속함.
- 이슈 시작 시점의 `MatchFoundService.process`는 user status `FOUND` 갱신을 session/timeout 생성보다 먼저 수행함.
- 이슈 시작 시점의 `MatchFoundService.process`는 session 저장 실패, timeout pending 등록 실패, `FOUND` status 일부 갱신 실패에 대한 queue 복귀 보상을 하지 않음.
- `MatchResponseResultService.timeoutWithLock`은 `MatchSession`이 있고 timeout pending job이 등록된 경우에만 deadline 정산을 수행할 수 있음.
- `MatchResponseResultService.returnAcceptedUserToQueue`는 실패 정산에서 수락 유저를 기존 `entryTime`과 `tierScore`로 queue에 복귀시키는 정책을 이미 사용함.
- `MatchFoundEvent` 이후 API 알림 계층은 Redis Pub/Sub과 SSE로 fan-out하며, Pub/Sub publish 실패는 알림 계층에서 로그로 격리하는 구조임.

### Package Boundary

| 영역 | 패키지 | 책임 |
|------|--------|------|
| 매칭 후보 탐색과 queue 원자 제거 | `league-of-star-matching` `domain/service` | FIFO 정렬, 후보 선택, `atomicPairRemove`, 후처리 호출 |
| match found 후처리 | `league-of-star-matching` `domain/service` | session 저장, timeout 등록, user status 전환, event 발행, 실패 보상 |
| Redis queue 저장소 | `league-of-star-matching` `repository`, `infrastructure/redis` | `MatchTicket` add/remove/findAll/atomicPairRemove |
| Redis user status 저장소 | `league-of-star-matching` `repository`, `infrastructure/redis` | `MATCHING`, `FOUND`, `ACCEPTED`, `IN_GAME` 등 점유 상태 저장 |
| Redis match session 저장소 | `league-of-star-matching` `repository`, `infrastructure/redis` | `match:session:{matchId}` 저장/조회/삭제 |
| Redis timeout index | `league-of-star-matching` `repository`, `infrastructure/redis` | response timeout pending/processing ZSET 관리 |
| timeout 정산 | `league-of-star-matching` `domain/service`, `scheduler` | 10초 deadline 이후 실패 조합 정산 |
| match_found 알림 fan-out | `league-of-star-api` `notification/match` | Spring event 수신, Redis Pub/Sub publish/subscribe, SSE 전송 |
| MatchTicket / MatchSession | `league-of-star-core` `domain/match/domain` | 매칭 ticket/session value object |

- 이번 이슈의 주 구현 위치는 `league-of-star-matching`의 `MatchFoundService`임.
- `MatchPairingService`의 paired 처리 시점과 scheduler 구조는 유지함.
- `league-of-star-api` notification 계층의 Pub/Sub/SSE 구조는 변경하지 않음.
- `MatchTicket` schema와 Redis queue key 구조는 변경하지 않음.
- `MatchSession` schema와 timeout scheduler 구조는 변경하지 않음.

### Recovery Policy

| 실패 지점 | 처리 |
|----------|------|
| session 저장 실패 | 두 유저를 기존 ticket으로 queue에 복귀시키고 status를 `MATCHING`으로 강제 복구 |
| timeout pending 등록 실패 | session 삭제 후 두 유저를 queue에 복귀시키고 status를 `MATCHING`으로 강제 복구 |
| userA `FOUND` 갱신 실패 | timeout cleanup, session 삭제, 두 유저 queue 복귀, status `MATCHING` 강제 복구 |
| userB `FOUND` 갱신 실패 | timeout cleanup, session 삭제, 두 유저 queue 복귀, status `MATCHING` 강제 복구 |
| event 발행 실패 | session/timeout/status 유지, queue 복귀 없음, 10초 timeout 정산에 위임 |
| 보상 작업 일부 실패 | 나머지 보상은 계속 시도하고 새 로그/metric 추가는 이번 이슈에서 보류 |

보상 정책 세부 기준은 다음과 같음.

- queue 복귀는 `matchQueueStore.add(originalTicket)`로 처리한다.
- queue 복귀 후 user status는 `userStatusStore.updateStatus(userId, MATCHING, STATUS_TTL_SECONDS)`로 강제 복구한다.
- `setStatusIfAbsent`는 사용하지 않는다. 일부 `FOUND`가 남은 경우 queue에 있는데 status가 `FOUND`인 불일치가 생길 수 있기 때문이다.
- event 발행 실패는 queue 복귀 보상 대상이 아니다. 일부 클라이언트가 이미 알림을 받았을 수 있으므로 세션을 유지한다.
- event 발행 실패 후 유저 응답이 없으면 기존 timeout 정산이 `PENDING + PENDING`으로 두 유저를 timeout 이탈 처리한다.
- session/timeout/status 생성 실패로 queue 복귀한 유저는 같은 scan cycle에서는 다시 매칭되지 않고 다음 tick에서 재매칭 대상이 된다.

### Scope Boundary

이번 이슈에 포함함.

- `MatchFoundService` 후처리 순서를 session 저장, timeout 등록, user status `FOUND`, event 발행 순서로 변경
- session 저장 실패 시 두 유저 queue 복귀
- timeout pending 등록 실패 시 session 삭제와 두 유저 queue 복귀
- user status `FOUND` 갱신 실패 시 timeout cleanup, session 삭제, 두 유저 queue 복귀
- queue 복귀 시 기존 `entryTime`과 `tierScore` 유지
- queue 복귀 유저의 status를 `MATCHING`으로 강제 복구
- event 발행 실패는 session/timeout/status 유지 후 timeout 정산에 위임
- 보상 작업을 best-effort로 분리해 하나 실패해도 나머지 보상을 계속 시도
- `MatchFoundServiceTest` 후처리 실패/복구 테스트 추가
- 정책 문서와 checkpoint 문서 갱신

이번 이슈에서 제외함.

- 동일 userId 셀프 매칭 방지
- 동일 IP 기반 어뷰징 방지 정책
- client IP 추출 및 proxy/load balancer header 신뢰 정책
- `MatchTicket` IP/hash 필드 추가
- Redis queue key 또는 Lua atomic remove 구조 변경
- `MatchSession` schema 변경
- timeout scheduler 구조 변경
- Redis Pub/Sub 보장형 delivery 구현
- DB outbox/saga 기반 복구
- metric 추가
- Grafana dashboard 수정
- accept/reject/timeout 정책 변경
- gameRoom 생성, WebSocket, record/rank 정산 변경

## 📚 Tasks

### 1. 정책과 현재 match_found 후처리 경계 확정

- [x] `atomicPairRemove` 이후 후처리 실패가 발생할 수 있는 지점을 session 저장, timeout 등록, user status 갱신, event 발행으로 구분함.
- [x] `FOUND` status 노출 전에 session과 timeout pending이 먼저 존재해야 한다는 정책을 확정함.
- [x] session 또는 timeout 생성 실패는 match found 미성립으로 보고 두 유저 모두 queue 복귀하는 것으로 확정함.
- [x] event 발행 실패는 match found session을 유지하고 timeout 정산에 위임하는 것으로 확정함.
- [x] queue 복귀 시 기존 `entryTime`과 `tierScore`를 유지하는 것으로 확정함.
- [x] queue 복귀 보상 시 status는 `MATCHING`으로 강제 복구하는 것으로 확정함.
- [x] 같은 scan cycle에서 재매칭하지 않고 다음 scheduler tick에서 재매칭하는 것으로 확정함.
- [x] metric/Grafana와 동일 userId 셀프 매칭 방지는 이번 이슈 범위에서 제외함.

확정한 구현 경계는 다음과 같음.

- `MatchFoundService`는 session/timeout/status/event 후처리와 실패 보상을 담당함.
- `MatchPairingService`는 기존처럼 queue 원자 제거와 paired set 관리를 담당함.
- `MatchResponseTimeoutService`와 `MatchResponseResultService.timeoutWithLock`는 기존 timeout 정산 흐름을 유지함.
- notification Pub/Sub/SSE 계층은 event 발행 이후의 fan-out만 담당하고, 이번 이슈에서는 변경하지 않음.
- `atomicPairRemove` 성공 후 session/timeout/status 생성 단계에서 실패하면 `MatchFoundService`가 두 유저 queue 복귀와 `MATCHING` status 복구를 best-effort로 수행함.
- `MatchFoundEvent` 발행 실패는 일부 클라이언트 수신 가능성을 고려해 복귀 보상을 수행하지 않고 기존 timeout 정산에 맡김.
- 동일 userId 셀프 매칭 방지와 운영 metric은 Issue 66 구현 완료 후 별도 이슈에서 다룸.

### 2. `MatchFoundService` 처리 순서 재구성

- [x] `MatchFoundService.process`에서 `matchId`와 `MatchSession`을 먼저 생성하도록 정리함.
- [x] `sessionStore.save(session, MATCH_SESSION_TTL_SECONDS)`를 user status 갱신보다 먼저 수행함.
- [x] `timeoutStore.addPending(matchId, deadlineMillis)`를 user status 갱신보다 먼저 수행함.
- [x] `userStatusStore.updateStatus(userA, FOUND, STATUS_TTL_SECONDS)`를 session/timeout 준비 후 수행함.
- [x] `userStatusStore.updateStatus(userB, FOUND, STATUS_TTL_SECONDS)`를 userA 갱신 다음에 수행함.
- [x] `eventPublisher.publishEvent(new MatchFoundEvent(...))`를 마지막 단계로 유지함.
- [x] 정상 흐름에서 기존 `match_found` payload와 accept timeout seconds가 변경되지 않도록 유지함.

구현 시 주의할 점은 다음과 같음.

- `clock.millis()` 기준 timeout deadline 계산 정책은 유지함.
- `UUID.randomUUID().toString()` 기반 matchId 생성 정책은 유지함.
- `MatchSession.create`에 전달하는 userId, tierScore, entryTime은 기존 ticket 값을 그대로 사용함.
- `MatchFoundEvent`의 userA/userB와 accept timeout seconds는 기존 이벤트 계약을 유지함.

### 3. session 저장 실패 보상 구현

- [x] `sessionStore.save` 실패를 `MatchFoundService` 내부에서 감지함.
- [x] session 저장 실패 시 timeout cleanup이나 session delete를 시도하지 않음.
- [x] session 저장 실패 시 userA/userB를 원래 `MatchTicket`으로 queue에 복귀시킴.
- [x] queue 복귀 후 userA/userB status를 `MATCHING`으로 강제 복구함.
- [x] userA queue 복귀 실패와 userB queue 복귀 실패를 각각 격리해 나머지 보상을 계속 시도함.
- [x] userA status 복구 실패와 userB status 복구 실패를 각각 격리해 나머지 보상을 계속 시도함.
- [x] session 저장 실패 시 `MatchFoundEvent`를 발행하지 않음.

완료 기준은 다음과 같음.

- session 저장 실패 후 두 유저 모두 `matchQueueStore.add(originalTicket)` 대상이 됨.
- session 저장 실패 후 두 유저 모두 `MATCHING` status 복구 대상이 됨.
- session 저장 실패 후 `timeoutStore.addPending`과 `eventPublisher.publishEvent`는 호출되지 않음.

### 4. timeout pending 등록 실패 보상 구현

- [x] `timeoutStore.addPending` 실패를 `MatchFoundService` 내부에서 감지함.
- [x] timeout 등록 실패 시 저장된 `MatchSession`을 삭제함.
- [x] timeout 등록 실패 시 userA/userB를 원래 `MatchTicket`으로 queue에 복귀시킴.
- [x] queue 복귀 후 userA/userB status를 `MATCHING`으로 강제 복구함.
- [x] session 삭제 실패가 발생해도 queue 복귀와 status 복구를 계속 시도함.
- [x] timeout 등록 실패 시 user status `FOUND` 갱신을 수행하지 않음.
- [x] timeout 등록 실패 시 `MatchFoundEvent`를 발행하지 않음.

완료 기준은 다음과 같음.

- timeout 등록 실패 후 `sessionStore.delete(matchId)`가 호출됨.
- timeout 등록 실패 후 두 유저 모두 기존 ticket으로 queue 복귀 대상이 됨.
- timeout 등록 실패 후 두 유저 모두 `MATCHING` status 복구 대상이 됨.
- timeout 등록 실패 후 `eventPublisher.publishEvent`는 호출되지 않음.

### 5. user status `FOUND` 갱신 실패 보상 구현

- [x] userA `FOUND` 갱신 실패를 감지함.
- [x] userB `FOUND` 갱신 실패를 감지함.
- [x] userA 또는 userB 중 한 명이라도 `FOUND` 갱신에 실패하면 해당 match found session을 폐기함.
- [x] `FOUND` 갱신 실패 시 timeout pending cleanup을 시도함.
- [x] `FOUND` 갱신 실패 시 `MatchSession` 삭제를 시도함.
- [x] `FOUND` 갱신 실패 시 userA/userB를 원래 `MatchTicket`으로 queue에 복귀시킴.
- [x] queue 복귀 후 userA/userB status를 `MATCHING`으로 강제 복구함.
- [x] userA `FOUND`는 성공하고 userB `FOUND`가 실패한 경우에도 두 유저 모두 queue 복귀와 `MATCHING` 복구 대상이 됨.
- [x] timeout cleanup 실패 또는 session 삭제 실패가 발생해도 queue 복귀와 status 복구를 계속 시도함.
- [x] `FOUND` 갱신 실패 시 `MatchFoundEvent`를 발행하지 않음.

완료 기준은 다음과 같음.

- 일부 `FOUND`만 성공한 불완전 match found session은 유지되지 않음.
- `FOUND` 갱신 실패 후 timeout pending과 session 삭제가 best-effort로 시도됨.
- `FOUND` 갱신 실패 후 두 유저 모두 기존 ticket으로 queue 복귀 대상이 됨.
- `FOUND` 갱신 실패 후 두 유저 모두 `MATCHING` status 복구 대상이 됨.

### 6. event 발행 실패 격리 구현

- [x] `eventPublisher.publishEvent` 실패를 session/status/timeout 복구 대상과 분리함.
- [x] event 발행 실패 시 session을 삭제하지 않음.
- [x] event 발행 실패 시 timeout pending을 cleanup하지 않음.
- [x] event 발행 실패 시 user status `FOUND`를 유지함.
- [x] event 발행 실패 시 userA/userB를 queue에 복귀시키지 않음.
- [x] event 발행 실패 시 session/status/timeout 결과를 되돌리지 않고 새 로그/metric은 추가하지 않음.
- [x] event 발행 실패 후 응답이 없으면 기존 timeout scheduler가 정산한다는 정책을 문서화함.

완료 기준은 다음과 같음.

- event 발행 실패는 `MatchFoundService.process`의 session/timeout/status 결과를 되돌리지 않음.
- event 발행 실패 후 timeout pending이 남아 있어 10초 deadline 정산이 가능함.
- event 발행 실패 후 `matchQueueStore.add`가 호출되지 않음.

### 7. best-effort 보상 helper 정리

- [x] queue 복귀 helper를 추가해 `matchQueueStore.add(originalTicket)` 호출을 한 곳에서 처리함.
- [x] status 복구 helper를 추가해 `userStatusStore.updateStatus(userId, MATCHING, STATUS_TTL_SECONDS)` 호출을 한 곳에서 처리함.
- [x] session 삭제 helper를 추가해 `sessionStore.delete(matchId)` 실패를 격리함.
- [x] timeout cleanup helper를 추가해 `timeoutStore.cleanup(matchId)` 실패를 격리함.
- [x] 한 보상 작업 실패가 다른 보상 작업을 중단하지 않도록 각 보상 단위를 별도 try-catch로 분리함.
- [x] 새 helper는 `MatchFoundService` 내부 private method로 유지하고 외부 API로 노출하지 않음.
- [x] 불필요한 추상화나 새 service 분리는 하지 않고 현재 `league-of-star-matching` 패키지 경계를 유지함.

구현 기준은 다음과 같음.

- session/timeout/status 생성 단계의 실패 처리만 보상 helper를 사용함.
- event 발행 실패는 보상 helper를 호출하지 않음.
- 이번 이슈에서는 새 로그 문구와 metric 호출을 추가하지 않음.

### 8. `MatchFoundServiceTest` 정상/실패 케이스 추가

- [x] 정상 흐름에서 session 저장, timeout 등록, userA/userB `FOUND` 갱신, event 발행 순서를 검증함.
- [x] session 저장 실패 시 queue 복귀와 `MATCHING` status 복구를 검증함.
- [x] session 저장 실패 시 timeout 등록과 event 발행이 호출되지 않는지 검증함.
- [x] timeout pending 등록 실패 시 session 삭제, queue 복귀, `MATCHING` status 복구를 검증함.
- [x] timeout pending 등록 실패 시 user status `FOUND` 갱신과 event 발행이 호출되지 않는지 검증함.
- [x] userA `FOUND` 갱신 실패 시 timeout cleanup, session 삭제, queue 복귀, `MATCHING` status 복구를 검증함.
- [x] userB `FOUND` 갱신 실패 시 userA까지 포함해 두 유저 모두 queue 복귀와 `MATCHING` status 복구 대상인지 검증함.
- [x] event 발행 실패 시 session/timeout/status를 유지하고 queue 복귀가 호출되지 않는지 검증함.
- [x] 보상 작업 일부가 실패해도 나머지 보상 작업을 계속 시도하는지 검증함.

테스트 기준은 다음과 같음.

- `ArgumentCaptor<MatchSession>`으로 저장된 session의 userId, tierScore, entryTime, status, response status를 검증함.
- `ArgumentCaptor<MatchTicket>`으로 queue 복귀 ticket이 원래 `entryTime`과 `tierScore`를 유지하는지 검증함.
- Mockito `InOrder`를 사용해 정상 흐름의 session, timeout, status, event 순서를 검증함.
- event 발행 실패 테스트는 `RuntimeException`을 던지게 하고 보상 호출이 없는지 검증함.
- metric 검증은 추가하지 않음.

### 9. `MatchPairingService` 회귀 확인

- [x] `atomicPairRemove` 성공 즉시 paired 처리하는 기존 흐름을 유지함.
- [x] 후처리 실패로 queue 복귀한 유저가 같은 scan cycle에서 재매칭되지 않고 다음 scheduler tick에서 처리되는 정책을 문서화함.
- [x] `MatchPairingService`에서 후처리 실패 예외를 catch하고 다음 후보 처리를 계속하는 기존 흐름을 유지함.
- [x] `MatchPairingServiceTest` 기존 paired 중복 방지 테스트가 깨지지 않는지 확인함.
- [x] 필요 시 후처리 실패 발생 시에도 scan loop가 중단되지 않는 회귀 테스트를 보강함.

완료 기준은 다음과 같음.

- `MatchPairingService`의 queue scan, 후보 선택, paired set 관리 책임은 변경되지 않음.
- 후처리 실패 보상은 `MatchFoundService` 내부 책임으로 유지됨.
- 기존 매칭 범위, Apex, 배치, atomicPairRemove 회귀 테스트가 통과함.

### 10. 문서와 checkpoint 갱신

- [x] `docs/antigravity/backend/plan-checkpoint.md` Step 16 체크리스트를 이번 이슈 정책에 맞게 갱신함.
- [x] Step 16 완료 후 구현 결과를 이 문서의 task 체크 상태와 Implementation Result 섹션에 반영함.
- [x] 동일 userId 셀프 매칭 방지는 Step 17 또는 별도 이슈로 분리되어 있음을 checkpoint에 유지함.
- [x] metric/Grafana 제외 정책을 문서에 남김.
- [x] outbox/saga 기반 보장형 복구는 후속 이슈 범위로 남김.

### 11. 검증

- [x] `./gradlew :league-of-star-matching:test`를 실행함.
- [x] `./gradlew test`를 실행함.
- [x] 신규 `MatchFoundServiceTest`가 정상/실패/보상 경로를 모두 통과하는지 확인함.
- [x] 기존 `MatchPairingServiceTest`, `MatchResponseResultServiceTest`, timeout scheduler 테스트가 통과하는지 확인함.
- [x] 테스트 결과를 이 문서 Implementation Result에 기록함.

## Implementation Result

- `MatchFoundService.process`는 session 저장, timeout pending 등록, userA/userB `FOUND` 갱신, `MatchFoundEvent` 발행 순서로 처리함.
- session 저장 실패 시 두 유저를 기존 `MatchTicket.entryTime`과 `tierScore`로 queue에 복귀시키고 `MATCHING` status를 best-effort로 복구함.
- timeout pending 등록 실패 시 저장된 session을 삭제한 뒤 두 유저 queue 복귀와 `MATCHING` status 복구를 best-effort로 수행함.
- user status `FOUND` 갱신 실패 시 timeout cleanup, session 삭제, 두 유저 queue 복귀와 `MATCHING` status 복구를 best-effort로 수행함.
- `MatchFoundEvent` 발행 실패는 session/status/timeout을 되돌리지 않고 기존 timeout scheduler 정산에 위임함.
- `MatchPairingService`는 기존처럼 `atomicPairRemove` 성공 즉시 paired 처리하고, 후처리 실패가 발생해도 scan loop를 계속 진행함.
- 동일 userId 셀프 매칭 방지, metric/Grafana 추가, outbox/saga 기반 보장형 복구는 이번 이슈에서 제외함.
- 검증 결과 `MatchFoundServiceTest`, `MatchPairingServiceTest`, `MatchResponseResultServiceTest`, `MatchResponseTimeoutSchedulerTest`, `:league-of-star-matching:test`, 전체 `./gradlew test`가 통과함.

## 변경 이력

| 날짜 | 변경 내용 |
| :--- | :--- |
| 2026-05-29 | Issue 66 match_found 후처리 실패 복구 정책, 구현 흐름, task 초안 작성 |
| 2026-05-29 | Step 10 문서/checkpoint 갱신 및 Issue 66 구현 결과 정리 |
| 2026-05-29 | Step 11 검증 완료 및 테스트 통과 결과 기록 |

## PR Message

````md
## 📌 Summary

이번 PR은 `atomicPairRemove` 이후 `match_found` 후처리 중간 단계에서 실패가 발생해도 유저가 큐에서 사라지거나 `FOUND` 상태에 갇히지 않도록 복구 흐름 정리.

핵심 정책.

- 유저에게 `FOUND`를 보여주기 전에 `MatchSession`과 timeout pending을 먼저 만든다.
- session 또는 timeout을 만들 수 없으면 매칭은 성립하지 않은 것으로 보고 두 유저를 queue에 되돌린다.
- queue 복귀 시 기존 `entryTime`, `tierScore`를 유지해 대기 시간과 매칭 범위가 깨지지 않게 한다.
- 한 명만 `FOUND`가 된 불완전 상태는 허용하지 않고, session/timeout을 정리한 뒤 두 유저 모두 `MATCHING`으로 복구한다.
- event 발행 실패는 이미 session/status/timeout이 완성된 상태이므로 되돌리지 않고 기존 10초 timeout 정산에 맡긴다.
- 동일 userId 셀프 매칭 방지, metric/Grafana, outbox/saga는 이번 이슈 범위에서 제외한다.

```mermaid
flowchart TD
    A[MatchPairingService] --> B{atomicPairRemove 성공?}
    B -->|No| C[다음 후보 탐색]
    B -->|Yes| D[pairedUserIds 등록]
    D --> E[MatchFoundService.process]

    E --> F[1. MatchSession 저장]
    F --> G[2. timeout pending 등록]
    G --> H[3. userA FOUND 갱신]
    H --> I[4. userB FOUND 갱신]
    I --> J[5. MatchFoundEvent 발행]

    F -. 실패 .-> R1[두 유저 queue 복귀 + MATCHING 복구]
    G -. 실패 .-> R2[session 삭제 + 두 유저 queue 복귀 + MATCHING 복구]
    H -. 실패 .-> R3[timeout cleanup + session 삭제 + 두 유저 queue 복귀 + MATCHING 복구]
    I -. 실패 .-> R3
    J -. 실패 .-> R4[session/status/timeout 유지]

    R1 --> N[다음 scheduler tick에서 재매칭 가능]
    R2 --> N
    R3 --> N
    R4 --> T[기존 10초 timeout scheduler가 정산]
```

## 📚 Changes

### 1. 정상 match_found 흐름을 먼저 안전하게 재정렬

기존 흐름은 `FOUND` 상태를 먼저 노출한 뒤 session과 timeout을 준비할 수 있었음.

이러면 중간에 실패했을 때 유저 입장에서는 매칭된 것처럼 보이지만, 서버에는 응답할 session이나 timeout 정산 경로가 없을 수 있음.

그래서 정상 흐름을 아래처럼 변경.

```mermaid
sequenceDiagram
    participant Pairing as MatchPairingService
    participant Found as MatchFoundService
    participant Session as MatchSessionStore
    participant Timeout as MatchTimeoutStore
    participant Status as MatchUserStatusStore
    participant Event as ApplicationEventPublisher

    Pairing->>Found: process(userA, userB)
    Found->>Session: save(MatchSession)
    Found->>Timeout: addPending(matchId)
    Found->>Status: userA = FOUND
    Found->>Status: userB = FOUND
    Found->>Event: publish MatchFoundEvent
```

선택 기준.

- `FOUND`는 유저에게 “이제 응답할 수 있다”는 의미.
- 그러려면 `FOUND`보다 먼저 응답 대상인 session과 자동 정산용 timeout이 필요.
- 그래서 session/timeout을 먼저 만들고, 그 다음에 status를 `FOUND`로 변경.

### 2. session 저장 실패는 “매칭 미성립”으로 처리

session 저장에 실패하면 matchId는 있어도 실제 응답할 match session이 없음.

이 상태에서 유저를 `FOUND`로 두거나 queue에서 제거된 채로 두면 유저가 사라진 것처럼 됨.

```mermaid
flowchart LR
    A[session 저장 실패] --> B[MatchSession 없음]
    B --> C[응답 불가능]
    C --> D[두 유저 queue 복귀]
    D --> E[status MATCHING 복구]
```

다른 선택지도 있었지만 제외.

- 로그만 남기기: 유저가 queue에도 없고 session도 없는 상태가 될 수 있어 제외
- 즉시 재시도 반복: Redis 저장 실패 상황에서 같은 실패를 반복할 수 있어 제외
- queue 복귀: 기존 매칭 흐름으로 되돌아가므로 가장 단순하고 안전함

### 3. timeout pending 실패는 session을 삭제하고 queue 복귀

session 저장은 성공했지만 timeout pending 등록이 실패하면, 유저가 응답하지 않았을 때 10초 후 정산할 방법이 없음.

즉 “매칭은 됐는데 자동 종료가 안 되는 session”이 됨.

```mermaid
flowchart LR
    A[session 저장 성공] --> B[timeout pending 등록 실패]
    B --> C[10초 timeout 정산 불가능]
    C --> D[session 삭제]
    D --> E[두 유저 queue 복귀]
    E --> F[status MATCHING 복구]
```

그래서 timeout이 없으면 해당 match session은 유지하지 않음.

`FOUND`를 아직 노출하기 전 단계이므로, 이 시점에는 되돌리는 것이 가장 자연스러움.

### 4. user status FOUND 실패는 부분 성공도 실패로 처리

userA는 `FOUND`가 됐는데 userB는 실패하는 식의 부분 성공은 허용하지 않음.

매칭은 두 명이 같은 session을 바라봐야 하는데, 한 명만 `FOUND`인 상태는 UX와 서버 상태가 모두 애매해짐.

```mermaid
flowchart TD
    A[userA FOUND 성공] --> B{userB FOUND 성공?}
    B -->|Yes| C[event 발행 단계로 진행]
    B -->|No| D[부분 FOUND 상태 발생]
    D --> E[timeout cleanup]
    E --> F[session 삭제]
    F --> G[두 유저 queue 복귀]
    G --> H[두 유저 MATCHING 복구]
```

여기서 `setStatusIfAbsent`를 쓰지 않고 `updateStatus(..., MATCHING)`으로 강제 복구.

이유는 간단함. 한 명이라도 이미 `FOUND`가 되어 있을 수 있으므로, “없을 때만 설정”으로는 불완전 상태를 고칠 수 없음.

### 5. event 발행 실패는 되돌리지 않고 timeout 정산에 위임

event 발행은 session, timeout, status가 모두 준비된 뒤 마지막에 발생.

이 단계에서 실패했다고 바로 queue 복귀하면 더 위험할 수 있음.

```mermaid
flowchart TD
    A[session 있음] --> D[event 발행 실패]
    B[timeout pending 있음] --> D
    C[두 유저 FOUND 상태] --> D

    D --> E{queue 복귀할까?}
    E -->|No| F[session/status/timeout 유지]
    F --> G[클라이언트 응답 없으면 10초 timeout 정산]
```

queue 복귀를 하지 않은 이유.

- 일부 listener나 클라이언트가 이미 이벤트를 받았을 수 있음.
- 이때 session을 삭제하면 “알림은 받았는데 응답할 session은 없는 상태”가 됨.
- 이미 timeout pending이 있으므로, 응답이 없으면 기존 scheduler가 정리 가능.

그래서 event 실패는 복구 대상이 아니라 격리 대상으로 판단.

### 6. 보상 로직은 best-effort helper로 제한

이번 이슈의 목표는 완전한 분산 트랜잭션이 아니라, Redis 후처리 실패로 유저가 유실되는 상황을 줄이는 것.

그래서 outbox/saga 같은 큰 구조는 넣지 않고, 현재 `MatchFoundService` 안에서 필요한 보상만 독립적으로 시도.

```mermaid
flowchart TD
    A[후처리 실패] --> B[session 삭제 시도]
    A --> C[timeout cleanup 시도]
    A --> D[userA queue 복귀 시도]
    A --> E[userB queue 복귀 시도]
    A --> F[userA MATCHING 복구 시도]
    A --> G[userB MATCHING 복구 시도]

    B -. 실패해도 .-> H[나머지 보상 계속]
    C -. 실패해도 .-> H
    D -. 실패해도 .-> H
    E -. 실패해도 .-> H
    F -. 실패해도 .-> H
    G -. 실패해도 .-> H
```

트레이드오프.

- outbox/saga: 더 강한 보장은 가능하지만 구현 범위가 커지고 이번 이슈의 목적을 넘어감
- metric/Grafana: 운영 관측에는 좋지만, 먼저 정책과 복구 흐름을 고정하는 것이 우선이라 제외
- best-effort helper: 완전 보장은 아니지만 현재 구조 안에서 유저 유실 위험을 가장 작게 줄일 수 있음

### 7. MatchPairingService 흐름은 유지

`MatchPairingService`는 여전히 queue scan, 후보 선택, `atomicPairRemove`, paired set 관리를 담당.

후처리 실패 보상은 `MatchFoundService` 책임으로 유지.

```mermaid
flowchart LR
    A[atomicPairRemove 성공] --> B[pairedUserIds 등록]
    B --> C[MatchFoundService.process]
    C -. 실패 .-> D[MatchFoundService 내부 보상]
    D --> E[같은 scan cycle에서는 재매칭 안 함]
    E --> F[다음 scheduler tick에서 다시 후보]
```

이렇게 한 이유는 역할을 섞지 않기 위함.

- PairingService는 “누구와 누구를 매칭할지”만 결정.
- FoundService는 “매칭 성사 후 상태를 어떻게 만들고 실패 시 어떻게 되돌릴지”를 책임.
- 같은 scan cycle에서 바로 재매칭하지 않게 해서 중복 매칭 위험 방지.

### 8. 테스트와 CI 리포트 보강

테스트는 정상 흐름뿐 아니라 실패 지점별 보상 흐름을 나눠 추가.

- 정상 흐름 순서 검증
- session 저장 실패 보상
- timeout pending 실패 보상
- userA/userB `FOUND` 실패 보상
- event 발행 실패 격리
- 보상 작업 일부 실패 시 나머지 보상 계속 시도
- `MatchPairingService` scan loop 회귀 확인

추가로 CI에서 테스트가 간헐적으로 실패할 때 원인을 바로 볼 수 있도록 실패 시 테스트 리포트 artifact 업로드.

## 📝 Note

검증한 명령.

- `./gradlew :league-of-star-matching:test`
- `./gradlew test`
- `./gradlew build`
- `./gradlew clean build --parallel --no-build-cache`
- `git diff --check`

빌드 중 자동 갱신되는 `openapi3.yaml` timestamp 변경은 Issue 66 범위가 아니므로 미포함.

## 📌 Related Issue
- Closes #66
````
