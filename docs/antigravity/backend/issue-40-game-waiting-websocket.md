# Issue 40. Game Waiting WebSocket

## 📌 Feature Description

`GO_TO_GAME_WAITING` 이후 gameRoom별 WebSocket 연결을 열고, 참가자 인증 및 연결/READY 상태를 관리한다.

이 이슈는 게임 대기방의 연결/READY 상태 관리까지만 다룬다.
미접속/READY timeout 실행 처리, RTT 측정, countdown, `GAME_START`, scenario 전달, SMITE 판정, `game_actions`, `game_records` 저장은 후속 이슈에서 구현한다.

매칭 SSE는 `match_response_result`까지 담당한다.
클라이언트는 `GO_TO_GAME_WAITING` 수신 시 매칭 SSE `EventSource.close()`를 호출하고, `game.webSocketUrl`로 gameRoom WebSocket에 연결한다.
`GO_TO_MATCH_START`, `RETURN_TO_MATCHING` 처리는 매칭 화면 전환 범위이며, 이 이슈는 게임 대기 WebSocket 연결 action만 다룬다.

```mermaid
flowchart TD
    A["SSE match_response_result<br/>GO_TO_GAME_WAITING"] --> B["Client EventSource.close()"]
    B --> C["Client sends WebSocket handshake request<br/>GET /ws/game/{gameRoomId}?token=..."]
    C --> D["HandshakeInterceptor.beforeHandshake"]
    D --> E{"JWT valid?"}
    E -->|"no"| F["Reject handshake<br/>WebSocket not connected"]
    E -->|"yes"| G{"gameRoom exists<br/>and status READY?"}
    G -->|"no"| H["Reject handshake<br/>WebSocket not connected"]
    G -->|"yes"| I{"user is gameRoom participant?"}
    I -->|"no"| J["Reject handshake<br/>WebSocket not connected"]
    I -->|"yes"| K["Store gameRoomId/userId<br/>in WebSocket session attributes"]
    K --> L["Handshake success<br/>101 Switching Protocols"]
    L --> M["afterConnectionEstablished"]
    M --> N["Server stores connection<br/>roomId + userId"]
    N --> O["Client MP4 preload"]
    O --> P["CLIENT_READY"]
    P --> Q{"both READY?"}
    Q -->|"no"| R["WAITING"]
    Q -->|"yes"| S["게임 시작 가능 상태 기록<br/>GAME_START는 후속 이슈"]
```

정책:

- WebSocket은 native WebSocket으로 구현한다. STOMP/SockJS는 MVP 이후 검토한다.
- 브라우저 native WebSocket은 커스텀 `Authorization` 헤더를 붙이기 어렵기 때문에 handshake에서는 `?token=` query parameter로 JWT를 검증한다.
- `/ws/game/{gameRoomId}` handshake 경로는 Spring Security whitelist에 열고, 실제 인증/인가 처리는 WebSocket handshake interceptor에서 수행한다.
- 인증 성공 후에도 gameRoom participant가 아니면 연결을 거부한다.
- `CLIENT_READY`는 WebSocket 대기 상태이며, DB `game_participants.status=READY`와 구분한다.
- GAME_START 이전 미접속/READY timeout은 후속 이슈에서 `ABORTED`로 처리하고, `game_records`와 LP는 반영하지 않는 정책으로 둔다.

native WebSocket 선택 이유:

| 비교 항목 | native WebSocket | STOMP/SockJS | 현재 판단 |
| :--- | :--- | :--- | :--- |
| 메시지 규모 | `CLIENT_READY`, 입장/이탈, 이후 RTT/SMITE처럼 소수의 명령 중심 | topic/subscribe/send 구조에 적합 | 현재 게임 대기방은 단순 명령형 통신이므로 native WebSocket이 충분함 |
| room 구조 | gameRoom 1개에 플레이어 2명 | 여러 topic, lobby, spectator, broadcast fan-out에 유리 | MVP는 1:1 gameRoom 기준이라 broker 추상화가 과함 |
| 지연/판정 제어 | handler에서 수신 시각, session, 상태를 직접 제어하기 쉬움 | broker/message mapping 계층을 거침 | 이후 RTT/SMITE 판정까지 고려하면 서버 수신 제어가 명확한 native WebSocket이 유리함 |
| 구현 복잡도 | message envelope, session registry를 직접 구현해야 함 | 프레임/구독/라우팅 모델을 제공 | 현재 필요한 기능이 작아서 직접 구현 비용이 낮음 |
| 프론트 연동 | 브라우저 기본 `WebSocket` API 사용 | STOMP client 의존성 필요 | 단순한 프론트 기술 스택 정책과 native WebSocket이 더 잘 맞음 |
| 확장성 | 다중 서버 fan-out, 재구독, 복잡한 topic은 직접 설계 필요 | broker/subscribe 기반 확장에 유리 | 관전, lobby chat, 다중 topic, broker fan-out이 필요해질 때 STOMP를 재검토함 |

## 📚 Tasks

### 1. 현재 구조와 패키지 경계 확정

- [x] `smite-api`에는 현재 `game.adapter`, `game.service`만 있으므로 WebSocket 전용 패키지를 새로 둔다.
- [x] `smite-api`가 WebSocket endpoint, handshake, session registry, message handler를 담당한다.
- [x] `smite-core`가 gameRoom 존재/상태/participant 검증을 담당한다.
- [x] `smite-matching`은 이번 이슈에서 변경하지 않는다.
- [x] `ParticipantStatus.READY`는 DB 게임 참가자 준비 상태, WebSocket `CLIENT_READY`는 대기방 연결 이후 클라이언트 준비 신호로 분리한다.
- [x] API WebSocket 계층에서 `GameRoomRepository`를 직접 호출하지 않는다.

패키지 기준:

```text
smite-api
  com.sang.smite.game.adapter
  com.sang.smite.game.service
  com.sang.smite.game.config
  com.sang.smite.game.websocket
  com.sang.smite.game.websocket.handler
  com.sang.smite.game.websocket.interceptor
  com.sang.smite.game.websocket.resolver
  com.sang.smite.game.websocket.dto
  com.sang.smite.game.websocket.session
  com.sang.smite.auth.infrastructure.jwt
  com.sang.smite.common.path.security

smite-core
  com.sang.smite.domain.game.service
  com.sang.smite.domain.game.repository
  com.sang.smite.domain.game.domain
  com.sang.smite.domain.game.domain.vo
  com.sang.smite.common.exception

smite-matching
  변경 없음
```

### 2. WebSocket 의존성과 endpoint 설정

- [x] `smite-api`에 `spring-boot-starter-websocket` 의존성 추가
- [x] `GameWebSocketConfig`를 `@Configuration`, `@EnableWebSocket`으로 추가
- [x] native WebSocket handler를 `/ws/game/{gameRoomId}`에 등록
- [x] handshake interceptor를 handler 등록에 연결
- [x] 현재 `WebConfig`에는 CORS 정책이 없으므로 allowed origins 정책을 명시적으로 결정
- [x] Spring Security whitelist에 `/ws/game/**` handshake 경로 추가
- [x] `/ws/game/**`는 HTTP 필터 인증을 우회하되, handshake interceptor에서 JWT 인증/인가를 수행한다고 주석 또는 문서로 명시

변경 파일:

```text
backend/smite-api/build.gradle
backend/smite-api/src/main/java/com/sang/smite/game/config/GameWebSocketConfig.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/handler/GameWaitingWebSocketHandler.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/interceptor/GameWebSocketHandshakeInterceptor.java
backend/smite-api/src/main/java/com/sang/smite/common/path/security/SecurityPath.java
```

구현 상태:

- `/ws/game/{gameRoomId}` endpoint와 handler/interceptor wiring까지 추가한다.
- Task 3에서 handshake interceptor에 실제 JWT/participant 검증을 연결한다.
- allowed origins는 현재 별도 CORS 정책이 없으므로 MVP 기준 `setAllowedOriginPatterns("*")`로 시작한다.

### 3. WebSocket handshake 인증/인가

- [x] `GameWebSocketHandshakeInterceptor` 추가
- [x] handshake query parameter `token` 추출
- [x] `JwtTokenProvider`로 JWT 검증
- [x] token에서 `userId` 추출
- [x] URI path에서 `gameRoomId` 추출
- [x] core `GameRoomReadService`로 gameRoom READY 상태와 participant 검증
- [x] 인증 실패 시 handshake 거부
- [x] gameRoom 미존재, READY 아님, participant 아님이면 handshake 거부
- [x] 성공 시 WebSocket session attributes에 `gameRoomId`, `userId` 저장
- [x] attributes key는 상수로 분리해서 handler와 공유
- [x] query parameter token 추출은 auth infra `JwtTokenResolver`로 분리
- [x] path에서 gameRoomId 추출은 `GameWebSocketPathResolver`로 분리
- [x] query parameter token 방식은 MVP 정책으로 문서화하고, 운영 보안 강화 시 cookie 또는 최초 메시지 인증 재검토

추가/변경 파일:

```text
backend/smite-api/src/main/java/com/sang/smite/auth/infrastructure/jwt/JwtTokenResolver.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/interceptor/GameWebSocketHandshakeInterceptor.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/resolver/GameWebSocketPathResolver.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/session/GameWebSocketSessionAttribute.java
backend/smite-core/src/main/java/com/sang/smite/domain/game/service/GameRoomReadService.java
backend/smite-core/src/main/java/com/sang/smite/domain/game/domain/GameRoom.java
```

처리 흐름:

```text
beforeHandshake
  -> JwtTokenResolver.resolveWebSocketToken(uri)
  -> JwtTokenProvider.validateToken(token)
  -> JwtTokenProvider.getUserId(token)
  -> GameWebSocketPathResolver.resolveGameRoomId(uri)
  -> GameRoomReadService.validateReadyParticipant(gameRoomId, userId)
  -> attributes.put(GAME_ROOM_ID, gameRoomId)
  -> attributes.put(USER_ID, userId)
  -> true 반환
```

실패 처리:

- token 누락 또는 JWT 검증 실패: handshake 거부, `401 Unauthorized`
- gameRoomId path 형식 오류: handshake 거부, `400 Bad Request`
- gameRoom 미존재, READY 아님, participant 아님: handshake 거부, `403 Forbidden`

### 4. gameRoom participant 검증 유스케이스

- [x] `GameRoomReadService` 추가
- [x] gameRoom 존재 여부 검증
- [x] gameRoom status가 `READY`인지 검증
- [x] 요청 userId가 participant인지 검증
- [x] 실패 시 core 예외로 표현
- [x] `GameRoom` 도메인에 participant 포함 여부 확인 메서드 추가 여부 검토
- [x] 기존 `CoreErrorCode.GAME_ROOM_NOT_FOUND`, `INVALID_GAME_STATE`, `INVALID_GAME_PARTICIPANTS`를 우선 재사용

추가/변경 파일:

```text
backend/smite-core/src/main/java/com/sang/smite/domain/game/service/GameRoomReadService.java
backend/smite-core/src/main/java/com/sang/smite/domain/game/domain/GameRoom.java
```

주의:

- `smite-api` WebSocket 계층은 JPA repository를 직접 호출하지 않는다.
- gameRoom 검증은 core service를 통해 수행한다.

### 5. WebSocket session registry 구현

- [x] gameRoomId/userId 기준 connection registry 추가
- [x] WebSocket session id로 roomId/userId 역조회가 가능하도록 관리
- [x] 동일 유저 중복 연결 시 기존 연결을 닫고 새 연결로 교체하는 정책 적용
- [x] 연결 성공 시 in-memory 참가자 입장 상태 저장
- [x] 연결 종료 시 in-memory 참가자 이탈 상태 반영
- [x] 양쪽 연결 여부 조회 기능 추가
- [x] `CLIENT_READY` 상태 저장 기능 추가
- [x] 양쪽 READY 여부 조회 기능 추가
- [x] registry는 DB 상태를 변경하지 않고 WebSocket 연결 상태만 관리

추가 파일:

```text
backend/smite-api/src/main/java/com/sang/smite/game/websocket/session/GameRoomWebSocketSessionRegistry.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/session/GameRoomWebSocketSession.java
```

정책:

- WebSocket connection은 서버 인스턴스 로컬 자원이므로 registry는 `smite-api`에 둔다.
- registry는 API 인스턴스 local memory 기반이므로, 멀티 인스턴스 환경에서는 같은 `gameRoomId`의 두 참가자가 같은 API 인스턴스로 라우팅되어야 한다.
- MVP 멀티 인스턴스 정책은 `/ws/game/{gameRoomId}`의 `gameRoomId` 기반 sticky routing이다.
- sticky 기준은 userId가 아니라 gameRoomId다. userId 기준 sticky는 같은 gameRoom의 두 유저가 서로 다른 인스턴스로 갈 수 있다.
- Redis registry/pub-sub 기반 fan-out은 sticky routing이 어렵거나 서버 장애 복구/관전/다중 topic 확장이 필요할 때 후속으로 검토한다.

멀티 인스턴스 문제와 해결:

```text
문제 상황:
  User A -> API-1 / room 100
  User B -> API-2 / room 100

결과:
  API-1 registry에는 A만 존재
  API-2 registry에는 B만 존재
  areBothConnected(room 100), areBothReady(room 100), room broadcast가 정확히 동작하지 않음

MVP 해결:
  /ws/game/100 요청은 항상 같은 API 인스턴스로 라우팅
  gameRoomId=100 -> API-2

결과:
  API-2 registry에 A/B session이 함께 존재
  in-memory registry로 연결/READY/broadcast 처리 가능
```

sticky routing과 pub/sub 트레이드오프:

| 항목 | gameRoomId sticky routing | Redis registry/pub-sub |
| :--- | :--- | :--- |
| 구현 복잡도 | 낮음. 애플리케이션 registry 구조 유지 | 높음. 연결 상태 저장, 메시지 fan-out, 장애 처리를 별도 설계 |
| 현재 코드 영향 | 작음. in-memory registry 유지 | 큼. registry/broadcast/message routing 구조 변경 필요 |
| 라우팅 전제 | 같은 gameRoomId가 같은 API 인스턴스로 가야 함 | 인스턴스가 달라도 메시지 전달 가능 |
| 장애 복구 | 해당 인스턴스 장애 시 연결 재수립 필요 | 설계에 따라 상태 복구와 재전송 전략 확장 가능 |
| MVP 적합성 | 높음. 1:1 gameRoom과 단순 메시지에 적합 | 과함. 관전/로비/다중 topic/fan-out 단계에서 적합 |

### 6. WebSocket message model 정의

- [x] client message 공통 envelope 정의: `type`, `payload`
- [x] server message 공통 envelope 정의: `type`, `payload`
- [x] `CLIENT_READY` message 정의
- [x] `PLAYER_JOINED`, `PLAYER_READY`, `PLAYER_LEFT`, `ERROR` server message 정의
- [x] 이번 이슈에서는 `GAME_START` message를 정의하거나 전송하지 않음
- [x] 잘못된 message type 처리 정책 정의
- [x] handler는 클라이언트가 보낸 `gameRoomId`, `userId` payload를 신뢰하지 않고 session attributes만 사용

추가 파일:

```text
backend/smite-api/src/main/java/com/sang/smite/game/websocket/dto/GameWebSocketClientMessage.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/dto/GameWebSocketServerMessage.java
backend/smite-api/src/main/java/com/sang/smite/game/websocket/dto/GameWebSocketMessageType.java
```

구현 상태:

- client envelope는 `type`, `payload`를 가진다.
- server envelope는 `type`, `payload`를 가진다.
- `GameWebSocketMessageType`은 client/server 방향을 구분한다.
- 서버 메시지는 `GameWebSocketServerMessage` factory로 생성한다.
- 잘못된 client message type은 `ERROR / INVALID_MESSAGE_TYPE`으로 응답한다.
- `GAME_START`는 이번 이슈 범위가 아니므로 message type에 포함하지 않는다.

### 7. 게임 대기 WebSocket handler 구현

- [x] `GameWaitingWebSocketHandler` 추가
- [x] `afterConnectionEstablished`에서 session attributes의 `gameRoomId`, `userId` 조회
- [x] session attributes가 없으면 연결 종료
- [x] registry에 roomId/userId/session 등록
- [x] 연결 성공 시 같은 room 참가자에게 `PLAYER_JOINED` 전송
- [x] `CLIENT_READY` 수신 시 ready 상태 반영
- [x] ready 상태 변경 시 `PLAYER_READY` 전송
- [x] 양쪽 READY여도 이번 이슈에서는 `GAME_START`를 보내지 않음
- [x] `afterConnectionClosed`에서 registry 제거
- [x] 같은 room 남은 참가자에게 `PLAYER_LEFT` 전송
- [x] handler 예외 발생 시 session 정리
- [x] JSON 파싱 실패 또는 알 수 없는 message type은 `ERROR` 전송 후 연결 유지 여부 결정

추가 파일:

```text
backend/smite-api/src/main/java/com/sang/smite/game/websocket/handler/GameWaitingWebSocketHandler.java
```

구현 상태:

- handler는 handshake interceptor가 저장한 session attributes의 `gameRoomId`, `userId`만 신뢰한다.
- 연결 성공 시 registry에 session을 등록하고 `PLAYER_JOINED`를 room 참가자에게 broadcast한다.
- `CLIENT_READY`만 유효한 client message로 처리한다.
- `PLAYER_READY`, `PLAYER_LEFT`, `ERROR`는 server message로만 사용한다.
- 양쪽 READY여도 `GAME_START`는 전송하지 않는다.
- 연결 종료 또는 transport error 시 registry를 정리하고 남은 참가자에게 `PLAYER_LEFT`를 broadcast한다.

### 8. GAME_START 이전 timeout 정책 문서화

- [x] `GO_TO_GAME_WAITING` 이후 waiting deadline 기준 정의
- [x] WebSocket 미접속 timeout 후속 이슈 범위로 분리
- [x] `CLIENT_READY` 미수신 timeout 후속 이슈 범위로 분리
- [x] GAME_START 이전 timeout은 gameRoom `ABORTED` 정책으로 처리한다고 문서화
- [x] GAME_START 이전 timeout은 game record/LP 미반영이라고 문서화
- [x] GAME_START 이후 disconnect 정책과 분리

주의:

- 이번 이슈에서는 timeout scheduler/worker와 abort 실행을 구현하지 않는다.
- timeout 실행 처리는 별도 후속 이슈에서 구현한다.

정책:

- `GO_TO_GAME_WAITING` 이후 gameRoom은 `READY` 상태로 대기한다.
- waiting deadline은 서버가 gameRoom `READY`를 확정하고 `GO_TO_GAME_WAITING` 발행을 시작한 서버 시각을 기준으로 계산한다.
- timeout duration 값은 후속 timeout 구현 이슈에서 확정한다.
- waiting deadline 안에 두 참가자가 WebSocket에 접속하고 `CLIENT_READY`를 보내야 다음 RTT 측정 단계로 넘어갈 수 있다.
- WebSocket 미접속, 연결 후 `CLIENT_READY` 미수신, RTT 단계 진입 전 대기 실패는 모두 `GAME_START` 이전 timeout으로 본다.
- `GAME_START` 이전 timeout은 실제 판이 시작되지 않은 실패이므로 gameRoom을 `ABORTED`로 정리한다.
- `GAME_START` 이전 timeout은 `game_records`를 생성하지 않고 LP/배치/승급전 결과도 반영하지 않는다.
- 두 플레이어 모두 점수 변동 없이 start 버튼 화면으로 복귀한다. 큐 자동 복귀는 하지 않는다.

상태 전이:

```text
GO_TO_GAME_WAITING
-> gameRoom.status = READY
-> waiting deadline 시작
-> WebSocket connect 대기
-> CLIENT_READY 대기

timeout 발생
-> gameRoom.status = ABORTED
-> game_participants.status = ABORTED 또는 DISCONNECTED
-> match:status:{userId} 제거
-> game_records 생성 없음
-> LP/배치/승급전 반영 없음
-> 클라이언트 start 버튼 화면 복귀
```

`GAME_START` 이후 disconnect는 위 timeout 정책과 분리한다.

- `GAME_START` 이후에는 이미 유효한 판이 시작된 상태이므로 disconnect만으로 gameRoom을 `ABORTED` 처리하지 않는다.
- disconnect한 유저는 이후 추가 입력을 할 수 없지만, disconnect 전에 서버가 수신한 `SMITE` 액션은 그대로 유효하다.
- `GAME_START` 이후에는 WebSocket 연결이 모두 끊겨도 gameRoom 종료 작업은 서버 timer/scheduler 기준으로 완료한다.
- 서버 timer/scheduler는 HP scenario의 종료 시각 또는 몬스터 사망 시각까지 진행한 뒤 최종 판정을 수행한다.
- 서버는 기존 gameStartTime, HP scenario, 서버 수신 액션 기준으로 게임을 끝까지 판정한다.
- 상대가 유효한 SMITE로 처치에 성공하면 서버 최종 판정 결과대로 승/패를 기록한다.
- 상대가 처치하지 못하고 드래곤이 자연사하면 무승부로 기록하고 LP는 변동하지 않는다.
- 양쪽 모두 disconnect해도 이미 수신된 액션이 없으면 자연사 기준 무승부로 본다.

### 9. 테스트

- [x] handshake JWT 누락/잘못된 token 실패 테스트
- [x] participant가 아닌 유저 handshake 실패 테스트
- [x] READY gameRoom participant handshake 성공 테스트
- [x] connection registry 등록/제거 단위 테스트
- [x] 동일 유저 중복 연결 시 기존 session 교체 테스트
- [x] `CLIENT_READY` 수신 시 ready 상태 반영 테스트
- [x] 양쪽 READY 전에는 게임 시작 이벤트를 보내지 않는지 테스트
- [x] core `GameRoomReadService` participant 검증 단위/JPA 테스트
- [x] session attributes에 저장된 roomId/userId만 사용하고 payload userId를 신뢰하지 않는지 테스트

테스트 기준:

- WebSocket handler/registry는 단위 테스트 중심으로 검증한다.
- core gameRoom 검증은 mock 단위 테스트와 필요 시 `@DataJpaTest`로 확인한다.
- controller RestDocs 대상은 아니므로 RestAssuredMockMvc 문서 테스트는 추가하지 않는다.

### 10. 문서

- [x] `plan-checkpoint` Step 3 체크 상태 갱신
- [x] 매칭 SSE와 game WebSocket 책임 경계 재확인
- [x] `match_response_result` 이후 클라이언트 `EventSource.close()` 책임 명시 유지
- [x] WebSocket endpoint와 message type 정리
- [x] GAME_START 이전 timeout 실행 처리는 후속 이슈라고 명시
- [x] GAME_START 이전 timeout은 record/LP 미반영 정책이라고 명시

## ✅ 완료 기준

- 클라이언트가 `/ws/game/{gameRoomId}`로 WebSocket 연결할 수 있다.
- JWT 인증 실패 또는 gameRoom participant가 아닌 유저는 연결할 수 없다.
- 서버가 gameRoom별 연결 참가자를 식별할 수 있다.
- 클라이언트가 `CLIENT_READY`를 보내면 서버가 ready 상태를 관리한다.
- 양쪽 READY 전에는 게임 시작 단계로 넘어가지 않는다.
- GAME_START 이전 timeout 실행 처리는 후속 이슈 범위로 문서화되어 있다.
- `game_records`와 LP는 이 이슈에서 반영하지 않는다.

## 📌 Summary

`GO_TO_GAME_WAITING` 이후 게임 대기 화면에서 사용할 gameRoom WebSocket 기반을 구현했습니다.
클라이언트는 매칭 SSE `match_response_result.action=GO_TO_GAME_WAITING` 수신 후 `EventSource.close()`를 호출하고, `/ws/game/{gameRoomId}?token=...`로 연결합니다.
서버는 handshake 단계에서 JWT와 gameRoom participant를 검증하고, 연결/READY 상태를 gameRoom 단위로 관리합니다.

```mermaid
flowchart TD
    A["SSE match_response_result<br/>GO_TO_GAME_WAITING"] --> B["Client EventSource.close()"]
    B --> C["WebSocket handshake<br/>/ws/game/{gameRoomId}?token=..."]
    C --> D{"JWT valid?"}
    D -->|"no"| E["Reject handshake"]
    D -->|"yes"| F{"READY gameRoom participant?"}
    F -->|"no"| E
    F -->|"yes"| G["Store session attributes<br/>gameRoomId, userId"]
    G --> H["Register local session<br/>gameRoomId + userId"]
    H --> I["Broadcast PLAYER_JOINED"]
    I --> J["Client MP4 preload"]
    J --> K["CLIENT_READY"]
    K --> L["Broadcast PLAYER_READY"]
    L --> M["Wait for RTT/GAME_START<br/>later issue"]
```

## 📚 Changes

- native WebSocket endpoint `/ws/game/{gameRoomId}`를 추가했습니다.
  - MVP 게임 대기방은 1:1 room, READY, 입장/이탈 수준의 단순 명령형 통신이므로 STOMP/SockJS 대신 native WebSocket을 선택했습니다.
  - `/ws/game/**`는 HTTP security whitelist에 두고, 실제 인증/인가는 handshake interceptor에서 처리하도록 책임을 분리했습니다.
- handshake 인증/인가 흐름을 분리했습니다.
  - query parameter `token`은 `JwtTokenResolver`가 추출합니다.
  - path의 `gameRoomId`는 `GameWebSocketPathResolver`가 해석합니다.
  - core `GameRoomReadService`가 READY 상태와 participant 여부를 검증합니다.
- WebSocket session registry를 추가했습니다.
  - API 인스턴스 local memory에서 `gameRoomId + userId + sessionId` 기준으로 연결을 관리합니다.
  - 동일 유저 중복 연결 시 기존 session을 교체합니다.
  - 멀티 인스턴스에서는 `gameRoomId` sticky routing을 전제로 문서화했습니다.
- WebSocket message model과 handler를 추가했습니다.
  - client message는 `CLIENT_READY`만 허용합니다.
  - server message는 `PLAYER_JOINED`, `PLAYER_READY`, `PLAYER_LEFT`, `ERROR`를 사용합니다.
  - handler는 client payload의 `userId/gameRoomId`를 신뢰하지 않고 handshake session attributes만 사용합니다.
- GAME_START 전후 정책을 정리했습니다.
  - GAME_START 이전 미접속/READY timeout은 후속 이슈에서 `ABORTED`, record/LP 미반영으로 처리합니다.
  - GAME_START 이후 disconnect는 gameRoom을 중단하지 않고 서버 timer/scheduler와 scenario/action 기준으로 정상 판정합니다.
- 테스트와 문서를 보강했습니다.
  - handshake, path/token resolver, session registry, message model, handler, core read service 단위/JPA 테스트를 추가/검증했습니다.
  - `plan-checkpoint`, `policy`, `overallplan`, `domain status`, `websocket client`, `DDL` 문서와 정합성을 맞췄습니다.

## 📝 Note

- 이번 이슈는 게임 대기방 WebSocket 기반만 만든다.
- 미접속/READY timeout 실행 처리는 후속 이슈에서 구현한다.
- RTT 측정, countdown, `GAME_START`, scenario 전달은 후속 이슈에서 구현한다.
- SMITE 입력과 서버 판정은 후속 이슈에서 구현한다.
- MVP에서는 native WebSocket + JSON message를 사용한다.
- STOMP/SockJS, Redis 기반 WebSocket session 공유, 다중 서버 fan-out은 MVP 이후 검토한다.

## 📌 Related Issue

- Closes #40

## 변경 이력

| 날짜 | 변경 내용 |
| :--- | :--- |
| 2026-05-14 | Issue 40 작업 문서 생성. 게임 대기 WebSocket 흐름, 패키지 구조, 인증/인가, READY 상태, timeout 정책 task 정리 |
| 2026-05-14 | Issue 40 범위를 WebSocket 연결/입장/READY 상태 관리로 조정. 미접속/READY timeout 실행 처리는 후속 이슈로 분리 |
| 2026-05-14 | 현재 패키지 구조 기준으로 WebSocket 설정, handshake 인증/인가, core read service, session registry, handler, 테스트 task 구체화 |
| 2026-05-14 | Task 1 완료. 소스 기준 기존 WebSocket 설정 없음, API/game WebSocket adapter와 core gameRoom 검증 책임 경계 확정 |
| 2026-05-14 | Task 2 완료. WebSocket 의존성, `/ws/game/{gameRoomId}` endpoint 설정, fail-closed handshake interceptor, `/ws/game/**` whitelist 추가 |
| 2026-05-15 | Task 3 완료. handshake JWT 인증, gameRoom READY/participant 인가, WebSocket session attributes 저장, 실패 status code 분리 |
| 2026-05-15 | Task 3 의존 작업으로 Task 4 완료. core `GameRoomReadService`와 `GameRoom.hasParticipant` 추가 |
| 2026-05-15 | Task 3 구조 개선. token query 추출을 `JwtTokenResolver`로 분리하고 WebSocket 패키지를 `interceptor`/`session` 기준으로 정리 |
| 2026-05-15 | Task 3 구조 개선. WebSocket path의 gameRoomId 추출을 `GameWebSocketPathResolver`로 분리하고 path separator 상수화 |
| 2026-05-15 | Task 5 완료. gameRoom/user/sessionId 기준 in-memory WebSocket session registry와 READY 상태 관리 추가 |
| 2026-05-15 | Task 5 멀티 인스턴스 정책 추가. local registry 문제와 gameRoomId sticky routing 해결 방식, Redis pub/sub 트레이드오프 명시 |
| 2026-05-15 | Task 6 완료. WebSocket client/server envelope, message type, server message factory, invalid message type error 정의 |
| 2026-05-15 | Task 7 완료. 게임 대기 WebSocket handler에서 연결 등록, CLIENT_READY, PLAYER_JOINED/READY/LEFT, ERROR 처리 구현 |
| 2026-05-18 | Task 8 완료. GAME_START 이전 timeout은 ABORTED 및 record/LP 미반영, GAME_START 이후 disconnect는 정상 판정 흐름 유지로 정책화 |
| 2026-05-18 | Task 9 완료. WebSocket handshake/handler/registry/message와 core GameRoomReadService 단위/JPA 테스트 검증 |
| 2026-05-18 | Task 10 완료. plan-checkpoint Step 3 구현 상태, SSE/WebSocket 책임 경계, EventSource.close 책임, endpoint/message 문서 정합성 확인 |
