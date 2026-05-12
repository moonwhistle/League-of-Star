# Issue-34: 매칭 응답 결과 API 응답 및 클라이언트 이벤트 설계

## 📌 Feature Description

매칭 수락/거절/타임아웃 처리 결과를 클라이언트가 일관된 방식으로 처리할 수 있도록 HTTP API 응답과 SSE 이벤트 payload를 설계합니다.

이번 이슈의 핵심 기준은 다음과 같습니다.

```text
HTTP accept/reject 응답:
- 내가 누른 버튼이 서버에 반영되었는지
- 즉시 반영 가능한 화면 동작이 있는지

SSE match response event:
- matchId 단위 최종 결과가 무엇인지
- 매칭 성공/실패 사유가 무엇인지
- 게임 대기 화면으로 이동할지
- 다시 매칭 대기 상태로 돌아갈지
- start 버튼 화면으로 돌아갈지
```

SSE 연결은 `match_found`를 받은 직후 닫지 않습니다. 클라이언트는 `match_response_result` 최종 이벤트를 받을 때까지 같은 SSE 연결을 유지하고, 화면 전이가 확정된 뒤 연결을 닫거나 다음 매칭 알림 수신을 위해 유지합니다.

동시 accept처럼 HTTP 응답과 SSE 최종 이벤트가 거의 동시에 발생하는 경우, 네트워크 상황에 따라 SSE 최종 이벤트가 HTTP 응답보다 먼저 도착할 수 있습니다. `match_response_result`는 해당 `matchId`의 최종 이벤트이므로 화면 전환 기준으로 HTTP 응답보다 우선합니다.

프론트 기대 흐름은 다음과 같습니다.

| 시나리오 | 화면 전이 |
| :--- | :--- |
| 둘 다 수락 | 게임 진행 대기 화면으로 이동. 상대 정보/닉네임 표시 |
| 한 명 거절, 상대 미응답 | 거절한 유저도 정산 대기 모달 유지. 상대는 10초 모달 유지 |
| 한 명 거절, 상대가 10초 안에 수락 | 거절 유저는 start 버튼 복귀. 수락 유저는 기존 entryTime으로 매칭 대기 상태 복귀 |
| 둘 다 거절 | 최종 정산 후 둘 다 start 버튼 복귀 |
| 한 명 수락, 상대 timeout | 수락한 유저는 매칭 대기 상태 복귀. timeout 유저는 start 버튼 복귀 |
| 둘 다 timeout | 둘 다 start 버튼 복귀 |

## 📚 Tasks

### 1. 응답/이벤트 책임 경계 확정

- [x] HTTP accept/reject 응답은 “내 요청 처리 결과”만 담당하도록 정의
- [x] SSE `match_response_result` 이벤트는 “matchId 최종 결과”만 담당하도록 정의
- [x] 모든 응답을 SSE로만 보낼지, HTTP ack + SSE final result로 나눌지 비교 후 확정
- [x] SSE 이벤트는 상대의 개별 accept/reject 로그가 아니라 최종 성공/실패 결과와 실패 사유만 전달하도록 정의
- [x] 같은 `matchId`에서 `match_response_result` 이벤트가 HTTP ack보다 화면 전환 우선순위가 높다는 정책 명시
- [x] 상대가 먼저 reject한 순간에는 미응답 상대에게 실패 이벤트를 보내지 않는 정책 명시
- [x] reject한 유저도 최종 정산 전까지 start 버튼으로 돌아가지 않고 `match_response_result`를 기다리는 정책 명시
- [x] timeout 결과는 HTTP 응답이 아니라 SSE 이벤트로 전달하는 방향 확정
- [x] 양쪽 accept는 즉시 최종 성공이므로 게임 대기 화면 이동 이벤트가 필요함을 명시

#### 설계 기준

```text
내가 버튼을 눌렀다
-> HTTP 응답으로 내 요청 반영 여부를 받는다

매칭 성공/실패가 최종 확정되었다
-> match_response_result SSE 이벤트로 최종 결과와 다음 화면 상태를 받는다

같은 matchId의 match_response_result 이벤트를 먼저 받았다
-> 이후 도착한 HTTP ack의 action은 화면 전환 기준으로 무시한다
```

#### HTTP + SSE를 나누는 이유

| 방식 | 장점 | 단점 | 판단 |
| :--- | :--- | :--- | :--- |
| 모든 결과를 SSE로 전달 | 클라이언트가 하나의 스트림만 구독하면 됨 | 버튼 요청 성공/실패를 HTTP 응답과 분리해서 기다려야 함. SSE 연결 실패 시 명령 결과 확인이 어려움 | 단독 사용은 부적합 |
| HTTP ack + SSE final result | 명령 성공/실패는 HTTP로 즉시 확인하고, 매칭 성공/실패 최종 결과는 SSE로 받음 | 응답 경로가 둘로 나뉨 | 현재 정책에 적합 |

이번 이슈의 기본 방향은 `HTTP ack + SSE final result`입니다.

#### Task 1 결론

- `accept/reject` HTTP 성공 응답은 최종 화면 전환을 담당하지 않습니다.
- HTTP 성공 응답은 버튼 요청이 서버에 반영되었다는 command ack입니다.
- 최종 화면 전환은 항상 `match_response_result` SSE 이벤트가 담당합니다.
- 따라서 성공 응답 body는 필수가 아니며, task 2에서 `200 OK` empty body를 우선 검토합니다.
- 실패 응답은 기존 예외 응답 정책을 유지하고, 클라이언트는 세션 없음/만료/이미 종료 같은 실패를 start 버튼 복귀 기준으로 처리할 수 있게 문서화합니다.

### 2. 공통 API 응답 모델 설계

- [x] accept/reject API 성공 응답은 공통 DTO를 만들지 않고 `200 OK` empty body로 유지
- [x] 성공 HTTP 응답에는 `matchId`, `action`, `sessionStatus`, `myResponse`, `opponent`를 포함하지 않음
- [x] 성공 HTTP 응답은 command ack만 의미하며 화면 전환 책임을 갖지 않음
- [x] 실패 HTTP 응답은 기존 전역 `ErrorResponse`를 재사용
- [x] 실패 HTTP 응답에도 별도 `action` 필드를 추가하지 않음
- [x] 상대 정보와 최종 화면 전환 정보는 최종 SSE 이벤트에서만 전달

#### 응답 예시

```http
HTTP/1.1 200 OK
Content-Length: 0
```

#### 실패 응답 예시

```json
{
  "timestamp": "2026-05-12T10:00:00",
  "status": 409,
  "code": "MATCH_009",
  "message": "이미 완료된 매칭 세션입니다.",
  "errors": null
}
```

### 3. accept API 응답 정책 정의

- [x] accept 성공 HTTP 응답은 모든 성공 케이스에서 `200 OK` empty body
- [x] 내가 accept했고 상대가 아직 미응답이면 HTTP는 command ack만 반환하고 모달은 유지
- [x] 내가 accept했고 상대도 accept 완료면 HTTP 응답과 별개로 `match_response_result` SSE 이벤트가 최종 화면 전환을 담당
- [x] 내가 accept했지만 상대가 이미 reject한 상태라면 HTTP 응답과 별개로 `match_response_result` SSE 이벤트가 큐 복귀 전환을 담당
- [x] 중복 accept는 기존 멱등 정책을 유지하고 `200 OK` empty body
- [x] accept 후 session이 최종 `ACCEPTED`가 되는 경우 상대 정보/게임 대기 정보는 SSE 이벤트에 포함

#### 주요 케이스

| 상태 | HTTP 응답 |
| :--- | :--- |
| `PENDING + PENDING`에서 내가 accept | `200 OK` empty body. 최종 전환 대기 |
| 상대가 accept한 뒤 내가 accept | `200 OK` empty body. 최종 전환은 SSE `GO_TO_GAME_WAITING` |
| 상대가 reject한 뒤 내가 accept | `200 OK` empty body. 최종 전환은 SSE `RETURN_TO_MATCHING` |
| 이미 내가 accept한 상태에서 accept 재시도 | `200 OK` empty body |

#### accept 성공 후 클라이언트 처리

- HTTP `200 OK`는 버튼 요청 반영 성공만 의미합니다.
- accept 버튼을 누른 클라이언트는 `match_response_result`를 받을 때까지 기존 모달 또는 대기 상태를 유지합니다.
- 같은 `matchId`의 `match_response_result`가 먼저 도착하면 이후 accept HTTP 응답은 화면 전환 기준으로 무시합니다.
- accept 실패 응답은 기존 `ErrorResponse`를 사용합니다. 단, `MATCH_RESPONSE_LOCK_FAILED`는 모달을 유지하고 SSE 최종 결과를 기다리며, 그 외 실패는 모달을 닫고 start 버튼 화면으로 복귀합니다.

### 4. reject API 응답 정책 정의

- [x] reject 성공 HTTP 응답은 모든 성공 케이스에서 `200 OK` empty body
- [x] 내가 reject하면 내 화면은 즉시 start 버튼으로 돌아가지 않고 정산 대기 상태 유지
- [x] reject 후 수락/거절 버튼은 비활성화하고 정산 대기 상태를 표시
- [x] 상대가 아직 미응답이면 session은 `FOUND`로 유지
- [x] 상대도 reject했거나 이미 응답 완료된 경우 session은 `DECLINED`
- [x] 내가 reject한 순간 상대에게 실패 이벤트를 보내지 않음
- [x] reject 응답에는 큐 복귀/화면 최종 전환 없음
- [x] reject한 유저도 최종 `match_response_result` SSE 이벤트로 `GO_TO_MATCH_START` 전환

#### 주요 케이스

| 상태 | HTTP 응답 |
| :--- | :--- |
| `PENDING + PENDING`에서 내가 reject | `200 OK` empty body. 정산 대기 |
| 상대가 accept한 뒤 내가 reject | `200 OK` empty body. 최종 전환은 SSE `GO_TO_MATCH_START` |
| 상대가 reject한 뒤 내가 reject | `200 OK` empty body. 최종 전환은 SSE `GO_TO_MATCH_START` |
| 이미 내가 reject한 상태에서 reject 재시도 | `200 OK` empty body |

#### reject 성공 후 클라이언트 처리

- HTTP `200 OK`는 버튼 요청 반영 성공만 의미합니다.
- reject 버튼을 누른 클라이언트는 start 버튼으로 즉시 복귀하지 않습니다.
- reject 이후 같은 모달에서 수락/거절 버튼을 비활성화하고 정산 대기 상태를 표시합니다.
- 같은 `matchId`의 `match_response_result`를 받으면 `action=GO_TO_MATCH_START` 기준으로 start 버튼 화면에 복귀합니다.
- reject 실패 응답은 기존 `ErrorResponse`를 사용합니다. 단, `MATCH_RESPONSE_LOCK_FAILED`는 모달을 유지하고 SSE 최종 결과를 기다리며, 그 외 실패는 모달을 닫고 start 버튼 화면으로 복귀합니다.

### 5. 완료/거절/timeout 세션의 에러 응답 정책 정리

- [x] 이미 `ACCEPTED` 세션에 대한 accept/reject 요청 응답 정책 정리
- [x] 이미 `DECLINED` 세션에 대한 accept/reject 요청 응답 정책 정리
- [x] 이미 `TIMEOUT` 세션에 대한 accept/reject 요청 응답 정책 정리
- [x] 세션 TTL 만료 시 에러 응답과 클라이언트 화면 전이 기준 정리
- [x] 기존 `MatchingErrorCode`와 클라이언트 기본 화면 전이 정책 연결 가능 여부 검토

#### 기본 방향

| 서버 상태 | API 응답 방향 |
| :--- | :--- |
| 세션 없음 / TTL 만료 | `410 MATCH_SESSION_EXPIRED` |
| 참여자가 아닌 유저 | `403 MATCH_SESSION_NOT_PARTICIPANT` |
| 이미 `ACCEPTED` | `409 MATCH_SESSION_ALREADY_COMPLETED` |
| 이미 `DECLINED` | `409 MATCH_SESSION_ALREADY_DECLINED` |
| 이미 `TIMEOUT` | `409 MATCH_SESSION_TIMEOUT` |
| 같은 유저가 이미 accept한 뒤 reject | `409 MATCH_SESSION_ALREADY_ACCEPTED` |
| 같은 유저가 이미 reject한 뒤 accept | `409 MATCH_SESSION_ALREADY_DECLINED` |
| matchId lock 획득 실패 | `409 MATCH_RESPONSE_LOCK_FAILED` |

#### 실패 응답 처리 원칙

- 실패 응답은 기존 전역 `ErrorResponse`를 그대로 사용합니다.
- 실패 응답에 `action`, `matchId`, `opponent`, `game` 같은 매칭 결과 필드를 추가하지 않습니다.
- 실패 응답은 최종 정산 이벤트가 아니므로 `match_response_result`를 대체하지 않습니다.
- 클라이언트는 대부분의 accept/reject 실패를 받으면 해당 matchId의 응답 모달을 닫고 start 버튼 화면으로 복구합니다.
- 단, `MATCH_RESPONSE_LOCK_FAILED`는 같은 matchId의 응답 또는 timeout 정산이 진행 중인 상태로 보고 모달을 유지한 채 `match_response_result` SSE 이벤트를 기다립니다.
- 필요하면 `code/message`는 토스트, 로깅, 디버깅에만 사용합니다.

#### 클라이언트 실패 처리 기준

| 실패 코드 | 클라이언트 처리 |
| :--- | :--- |
| `MATCH_006` / `MATCH_SESSION_EXPIRED` | start 버튼 화면 복귀 |
| `MATCH_007` / `MATCH_SESSION_NOT_PARTICIPANT` | start 버튼 화면 복귀 |
| `MATCH_008` / `MATCH_SESSION_ALREADY_ACCEPTED` | start 버튼 화면 복귀 |
| `MATCH_009` / `MATCH_SESSION_ALREADY_COMPLETED` | start 버튼 화면 복귀 |
| `MATCH_010` / `MATCH_SESSION_ALREADY_DECLINED` | start 버튼 화면 복귀 |
| `MATCH_011` / `MATCH_SESSION_TIMEOUT` | start 버튼 화면 복귀 |
| `MATCH_012` / `MATCH_RESPONSE_LOCK_FAILED` | 모달 유지, 버튼 비활성화, SSE 최종 결과 대기 |

#### 실패 응답 예시

```json
{
  "timestamp": "2026-05-12T10:00:00",
  "status": 410,
  "code": "MATCH_006",
  "message": "매칭 수락 시간이 만료되었습니다.",
  "errors": null
}
```

### 6. SSE 최종 결과 이벤트 설계

- [x] 이벤트 이름 결정
  - `match_response_result`
- [x] 통합 이벤트 payload 정의
  - `matchId`
  - `outcome`
  - `reason`
  - `action`
  - `opponent`
  - `game`
- [x] `outcome` enum 정의
  - `MATCHED`
  - `FAILED`
- [x] `outcome` enum별 의미 문서화
- [x] `reason` enum 정의
  - `BOTH_ACCEPTED`
  - `MY_REJECTED`
  - `OPPONENT_REJECTED`
  - `MY_TIMEOUT`
  - `OPPONENT_TIMEOUT`
  - `BOTH_TIMEOUT`
- [x] `reason` enum별 의미 문서화
- [x] `action` enum 정의
  - `GO_TO_GAME_WAITING`
  - `GO_TO_MATCH_START`
  - `RETURN_TO_MATCHING`
- [x] `action` enum별 프론트 화면 전환 의미 문서화
- [x] `outcome/reason/action` 조합별 프론트 처리 매핑표 정의
- [x] 이벤트 payload에 상대 nickname/tier/tierScore 포함
- [x] `match_response_result` 이벤트 이름 자체가 최종 이벤트임을 클라이언트 정책에 명시

#### SSE result code mapping

| `outcome` | `reason` | `action` | 프론트 처리 |
| :--- | :--- | :--- | :--- |
| `MATCHED` | `BOTH_ACCEPTED` | `GO_TO_GAME_WAITING` | 게임 대기 화면 이동 |
| `FAILED` | `MY_REJECTED` | `GO_TO_MATCH_START` | start 버튼 화면 복귀 |
| `FAILED` | `OPPONENT_REJECTED` | `RETURN_TO_MATCHING` | 매칭 대기 상태 복귀 |
| `FAILED` | `MY_TIMEOUT` | `GO_TO_MATCH_START` | start 버튼 화면 복귀 |
| `FAILED` | `OPPONENT_TIMEOUT` | `RETURN_TO_MATCHING` | 매칭 대기 상태 복귀 |
| `FAILED` | `BOTH_TIMEOUT` | `GO_TO_MATCH_START` | start 버튼 화면 복귀 |

#### 이벤트 예시

```json
{
  "matchId": "match-1",
  "outcome": "FAILED",
  "reason": "OPPONENT_REJECTED",
  "action": "RETURN_TO_MATCHING",
  "opponent": {
    "userId": 2,
    "nickname": "opponent",
    "tier": "GOLD_IV",
    "tierScore": 13
  },
  "game": null
}
```

#### 구현 위치

- 이벤트 이름: `MatchNotificationEventName.MATCH_RESPONSE_RESULT`
- payload DTO: `MatchResponseResultNotification`
- enum: `MatchResponseOutcome`, `MatchResponseReason`, `MatchResponseAction`

### 7. 시나리오별 SSE 이벤트 발행 정책

- [ ] 둘 다 accept
  - 양쪽 모두에게 `outcome=MATCHED`, `reason=BOTH_ACCEPTED`, `action=GO_TO_GAME_WAITING`
  - 동시에 accept한 경우 한쪽 HTTP ack보다 SSE 최종 이벤트가 먼저 도착할 수 있으므로 클라이언트는 `match_response_result` 이벤트를 우선 적용
- [ ] 내가 reject
  - HTTP 응답은 `200 OK` empty body
  - 버튼은 비활성화하고 정산 대기 상태를 표시
  - 상대에게는 즉시 실패 이벤트를 보내지 않음
  - 상대가 제한 시간 안에 accept하거나 timeout 정산이 끝났을 때 양쪽 필요한 대상에게 최종 실패 이벤트를 보냄
- [ ] 상대 reject 후 내가 accept
  - 나에게 `outcome=FAILED`, `reason=OPPONENT_REJECTED`, `action=RETURN_TO_MATCHING`
- [ ] 둘 다 reject
  - 양쪽 모두 `outcome=FAILED`, 각자 `reason=MY_REJECTED`, `action=GO_TO_MATCH_START`
- [ ] 내가 accept, 상대 timeout
  - 나에게 `outcome=FAILED`, `reason=OPPONENT_TIMEOUT`, `action=RETURN_TO_MATCHING`
- [ ] 내가 timeout
  - 나에게 `outcome=FAILED`, `reason=MY_TIMEOUT`, `action=GO_TO_MATCH_START`
- [ ] 둘 다 timeout
  - 양쪽 모두 `outcome=FAILED`, `reason=BOTH_TIMEOUT`, `action=GO_TO_MATCH_START`

### 8. 기존 match_found SSE 흐름과 연결

- [ ] `match_found` payload는 유지
- [ ] 클라이언트는 `match_found` 수신 후 SSE를 닫지 않고 최종 `match_response_result` 또는 게임 이동 이벤트까지 유지하도록 정책 명시
- [ ] start 버튼 복귀, 게임 대기 화면 이동, 매칭 대기 복귀 시점별 SSE close/keep 정책 정의
- [ ] `match_response_result` 이벤트를 notification 패키지에 추가할지 검토
- [ ] Redis Pub/Sub message 추가 여부 결정
- [ ] 멀티 인스턴스에서 대상 유저 SSE 연결로 이벤트 전달되는지 확인
- [ ] 기존 `match_found` SSE 회귀 테스트 유지

### 9. 구현 구조 설계

- [ ] matching service 내부 결과 모델과 API DTO 분리
- [ ] API 응답 DTO는 `smite-api` controller response 패키지에 위치
- [ ] SSE payload DTO는 notification dto 패키지에 위치
- [ ] matching 모듈은 클라이언트 DTO에 직접 의존하지 않도록 설계
- [ ] 이벤트 발행 책임 위치 결정
  - matching service
  - api service
  - notification bridge
- [ ] 상대 nickname 조회 책임 위치 결정
  - API layer에서 user/rank/profile 조회 후 조립
- [ ] 상대 tier/tierScore 조회 책임 위치 결정
  - API layer에서 rank/profile 조회 후 조립

### 10. API 문서 및 RestDocs 갱신

- [ ] accept API 성공 응답 문서화
- [ ] reject API 성공 응답 문서화
- [ ] 주요 실패 응답 문서화
- [ ] `match_response_result` SSE 이벤트 payload 문서화
- [ ] `match_response_result.outcome` enum 표 문서화
- [ ] `match_response_result.reason` enum 표 문서화
- [ ] `match_response_result.action` enum 표 문서화
- [ ] `outcome/reason/action` 조합별 프론트 처리 매핑표 문서화
- [ ] `MatchControllerRestDocsTest` 갱신
- [ ] notification RestDocs 또는 별도 이벤트 문서 갱신

### 11. 테스트 작성

- [ ] accept API 응답 DTO 테스트
- [ ] reject API 응답 DTO 테스트
- [ ] accept controller/service 테스트 갱신
- [ ] reject controller/service 테스트 갱신
- [ ] 양쪽 accept 완료 이벤트 테스트
- [ ] reject 후 상대 모달 유지 정책 테스트
- [ ] reject 후 상대 accept 시 큐 복귀 이벤트 테스트
- [ ] timeout 정산 후 이벤트 테스트
- [ ] 기존 `match_found` SSE 회귀 테스트

## 📝 Note

### 설계 원칙

- HTTP 응답과 SSE 이벤트의 책임을 섞지 않습니다.
- HTTP 응답은 “내 요청 반영 결과”를 즉시 알려줍니다.
- SSE 이벤트는 “matchId 최종 결과”를 알려줍니다.
- 클라이언트는 `match_found` 수신 직후 SSE를 닫지 않고 최종 결과 이벤트까지 유지합니다.
- 상대가 먼저 reject한 사실을 즉시 알려서 미응답 유저의 10초 선택권을 깨지 않습니다.
- timeout은 서버 scheduler 결과이므로 SSE 이벤트가 필요합니다.

### 프론트 화면 상태 기준

HTTP 성공 응답은 `200 OK` empty body이므로 `WAIT_FOR_OPPONENT`, `WAIT_FOR_RESULT`를 서버 payload로 받지 않습니다.
두 상태는 클라이언트가 사용자가 누른 버튼과 HTTP 성공 여부를 기준으로 전환하는 로컬 UI 상태입니다.

| 로컬 상태 | 화면 |
| :--- | :--- |
| `WAIT_FOR_OPPONENT` | 현재 매칭 수락/거절 모달 유지 |
| `WAIT_FOR_RESULT` | 수락/거절 버튼 비활성화 후 정산 대기 |

`match_response_result` SSE 이벤트의 `action`은 최종 화면 전환만 표현합니다.

| SSE `action` | 화면 |
| :--- | :--- |
| `GO_TO_GAME_WAITING` | 게임 진행 대기 화면 이동 |
| `GO_TO_MATCH_START` | 매칭 끊김, start 버튼 화면 복귀 |
| `RETURN_TO_MATCHING` | 기존 우선순위로 매칭 대기 상태 표시 |

### SSE 연결 유지 정책

| 상황 | SSE 연결 |
| :--- | :--- |
| start 버튼 클릭 후 매칭 대기 | 연결 |
| `match_found` 수신 후 수락/거절 모달 표시 | 유지 |
| 둘 다 accept 후 게임 대기 화면 이동 | 클라이언트가 닫거나 게임용 연결로 전환 |
| 내가 reject 후 정산 대기 | 유지 |
| reject 최종 정산 후 start 버튼 복귀 | 클라이언트가 닫음 |
| 내가 accept 후 상대 reject/timeout으로 매칭 대기 복귀 | 새 `match_found`를 받을 수 있도록 유지 가능 |
| 내가 timeout 또는 둘 다 timeout 후 start 버튼 복귀 | 클라이언트가 닫음 |

### 우선 결정해야 할 질문

- 양쪽 accept 시 게임 세션 생성이 이번 이슈 범위인지, 이벤트 payload만 먼저 정의할지?
- `match_response_result` 하나로 모든 최종 결과를 통합할지, `game_ready`만 별도 이벤트로 분리할지?
- 이미 종료된 세션에 대한 API 요청을 error로만 줄지, 현재 상태 응답 body를 줄지?
- timeout 정산 이벤트 발행 실패가 timeout 정산 성공을 깨야 하는지, 아니면 별도 재전송/로그로 처리할지?
