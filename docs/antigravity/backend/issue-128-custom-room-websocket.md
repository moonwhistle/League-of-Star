# Issue 128. Custom Room WebSocket 동기화 계약

## 📌 Feature Description

사용자 지정 방 대기실에서 참가자 입장/퇴장과 방 상태 변경을 같은 room 참가자들에게 실시간으로 전달하는 Custom Room WebSocket 동기화 기능을 구현한다.

이번 이슈는 issue-126에서 만든 join/leave HTTP command 다음 단계다. issue-126은 DB 상태를 바꾸는 작업이고, 이번 이슈는 그 변경 결과를 다른 참가자 화면에 전달하는 작업이다.

```mermaid
flowchart TD
    A["create/join HTTP 성공"] --> B["CustomRoomPage 진입"]
    B --> C["WS 연결<br/>/ws/custom-games/rooms/{roomId}?token=..."]
    C --> D["Handshake<br/>token + participant 검증"]
    D --> E{"검증 성공?"}
    E -->|NO| F["Handshake 거부"]
    E -->|YES| G["roomId/userId session 등록"]
    G --> H["현재 room snapshot<br/>ROOM_UPDATED 전송"]

    I["다른 사용자 join/leave HTTP command"] --> J["DB 상태 변경 commit"]
    J --> K{"room 상태"}
    K -->|WAITING| L["ROOM_UPDATED broadcast"]
    K -->|CLOSED| M["ROOM_CLOSED broadcast"]
    M --> N["room sessions close/unregister"]
```

핵심 정책은 다음과 같다.

- Custom Room WebSocket은 CustomRoomPage에서만 연결한다.
- 연결 source는 `roomId`와 access token이다.
- access token은 기존 Game WebSocket과 동일하게 query parameter로 전달한다.
- WebSocket 연결은 DB 참가 상태를 만들지 않는다.
- 참가 상태 변경 source of truth는 issue-126의 HTTP join/leave command다.
- WebSocket은 DB 변경 결과를 같은 room session에 전달하는 동기화 채널이다.
- WebSocket disconnect/error는 DB leave가 아니다.
- disconnect/error 시에는 local session registry에서만 제거한다.
- 참가자 여부 검증은 core read service를 통해 수행한다.
- WebSocket 인증, session registry, message 전송은 api 모듈 책임이다.
- `ROOM_STARTED`는 후속 4-6 범위이며 이번 이슈에서 구현하지 않는다.
- 새 외부 패키지를 추가하지 않는다.

## Backend Contract

### Custom Room WebSocket Endpoint

```http
GET /ws/custom-games/rooms/{roomId}?token={accessToken}
```

native WebSocket은 일반 HTTP Authorization header 사용이 제한적이므로 기존 Game WebSocket과 동일하게 `token` query parameter를 사용한다.

### Handshake Contract

Handshake에서 다음 순서로 검증한다.

1. `token` query parameter를 추출한다.
2. JWT token을 검증한다.
3. token에서 `userId`를 추출한다.
4. path에서 `roomId`를 추출한다.
5. core `CustomGameRoomReadService`로 `WAITING` room participant인지 검증한다.
6. 성공 시 session attributes에 `customRoomId`, `userId`를 저장한다.

#### Handshake Error Policy

| 상황 | HTTP status | 기준 |
|------|-------------|------|
| token 없음/만료/유효하지 않음 | `401` | 기존 `JwtTokenResolver`, `JwtTokenProvider` 정책 |
| roomId path 없음/숫자 아님/양수 아님 | `400` | path resolver validation |
| room 없음 | `403` | core `CUSTOM_ROOM_NOT_FOUND` |
| room이 `WAITING`이 아님 | `403` | core `CUSTOM_ROOM_INVALID_STATE` |
| room participant가 아님 | `403` | core `CUSTOM_ROOM_INVALID_PARTICIPANT` |

### Server Message Envelope

```json
{
  "type": "ROOM_UPDATED",
  "payload": {
    "roomId": 1,
    "roomName": "Host's room",
    "inviteCode": "AB12CD",
    "ownerUserId": 10,
    "status": "WAITING",
    "maxParticipants": 2,
    "participants": [
      {
        "userId": 10,
        "nickname": "Host",
        "role": "OWNER"
      },
      {
        "userId": 20,
        "nickname": "Guest",
        "role": "PLAYER"
      }
    ]
  }
}
```

```json
{
  "type": "ROOM_CLOSED",
  "payload": {
    "roomId": 1,
    "roomName": "Host's room",
    "inviteCode": "AB12CD",
    "ownerUserId": 10,
    "status": "CLOSED",
    "maxParticipants": 2,
    "participants": []
  }
}
```

```json
{
  "type": "ERROR",
  "payload": {
    "code": "INVALID_MESSAGE_TYPE",
    "reason": "Custom Room WebSocket does not accept client messages in this issue."
  }
}
```

### Message Type Policy

| type | direction | 기준 |
|------|-----------|------|
| `ROOM_UPDATED` | server -> client | room이 `WAITING` 상태이고 participant 목록이 바뀐 경우 |
| `ROOM_CLOSED` | server -> client | 방장 leave로 room이 `CLOSED` 된 경우 |
| `ERROR` | server -> client | client가 이번 이슈에서 지원하지 않는 message를 보낸 경우 |
| `ROOM_STARTED` | server -> client | 후속 4-6 범위 |

Client message는 이번 이슈에서 처리하지 않는다. client가 text message를 보내면 `ERROR`를 응답하고 연결은 유지한다.

### Session Management Contract

Custom Room WebSocket session registry는 API 인스턴스 local memory에서 관리한다.

```mermaid
flowchart TD
    A["afterConnectionEstablished"] --> B["register(roomId, userId, session)"]
    B --> C{"같은 room/user 기존 session 존재?"}
    C -->|YES| D["기존 session close"]
    C -->|NO| E["새 session 저장"]
    D --> E
    E --> F["sessionsByRoom[roomId][userId] 저장"]
    E --> G["sessionsById[sessionId] 저장"]

    H["afterConnectionClosed / transport error"] --> I["unregister(sessionId)"]
    I --> J["registry에서만 제거"]
    J --> K["DB participant 유지"]
```

관리 기준:

- `roomId -> userId -> session` index를 둔다.
- `sessionId -> session` 역방향 index를 둔다.
- 같은 room/user가 재연결하면 기존 session을 닫고 새 session으로 교체한다.
- room 전체 close가 필요하면 해당 room sessions에 message를 보낸 뒤 close/unregister한다.
- disconnect/error는 session registry에서만 제거한다.
- disconnect/error에서 leave API를 호출하거나 participant row를 삭제하지 않는다.
- 멀티 인스턴스 운영에서는 같은 room이 같은 API 인스턴스로 라우팅되어야 한다. 이번 이슈에서는 기존 Game WebSocket과 동일하게 local memory registry로 제한한다.

### Broadcast Contract

join/leave HTTP command 이후 WebSocket event는 transaction commit 이후 전송한다.

```mermaid
sequenceDiagram
    participant U as 사용자
    participant API as API service
    participant Core as Core command/read
    participant DB as DB
    participant WS as CustomRoom WebSocket

    U->>API: join/leave HTTP
    API->>Core: command 실행
    Core->>DB: room/participant 변경
    API->>Core: 최신 participant 조회
    API-->>U: CustomRoomResponse
    API->>WS: afterCommit broadcast 예약
    WS-->>WS: ROOM_UPDATED 또는 ROOM_CLOSED 전송
```

전송 기준:

- `joinRoom()` 성공 후 room 상태가 `WAITING`이면 같은 room sessions에 `ROOM_UPDATED`를 보낸다.
- 일반 참가자 `leaveRoom()` 성공 후 room 상태가 `WAITING`이면 남은 room sessions에 `ROOM_UPDATED`를 보낸다.
- 일반 참가자 leave를 호출한 user의 session이 registry에 남아 있으면 close/unregister한다.
- 방장 `leaveRoom()` 성공 후 room 상태가 `CLOSED`이면 room sessions에 `ROOM_CLOSED`를 보낸 뒤 close/unregister한다.
- command 실패 시 WebSocket event를 보내지 않는다.

### Source Of Truth Policy

- room/participant DB 상태 source of truth는 core custom room domain이다.
- WebSocket session source of truth는 api local memory registry다.
- WebSocket event payload는 API `CustomRoomResponse`를 재사용한다.
- participant nickname은 기존 `UserReadService.findAllByIdsOrThrow` batch 조회로 조립한다.
- HTTP join/leave 응답은 호출자에게 주는 command 결과다.
- 다른 참가자 화면 반영 기준은 `ROOM_UPDATED`, `ROOM_CLOSED` event다.

## Scope Boundary

이번 이슈에 포함:

- `/ws/custom-games/rooms/{roomId}?token={accessToken}` endpoint 구현.
- Custom Room WebSocket config 구현.
- Custom Room WebSocket handshake interceptor 구현.
- Custom Room WebSocket path resolver 구현.
- core `CustomGameRoomReadService.validateWaitingParticipant(roomId, userId)` 구현.
- Custom Room 전용 session attribute, session object, session registry 구현.
- Custom Room server message DTO 구현.
- `ROOM_UPDATED`, `ROOM_CLOSED`, `ERROR` message type 구현.
- 연결 성공 시 현재 room snapshot을 연결된 session에 `ROOM_UPDATED`로 전송.
- join 성공 후 `ROOM_UPDATED` broadcast.
- 일반 참가자 leave 성공 후 `ROOM_UPDATED` broadcast.
- 방장 leave 성공 후 `ROOM_CLOSED` broadcast 및 room sessions close.
- WebSocket disconnect/error 시 session registry만 정리.
- transaction commit 이후 broadcast하도록 notifier 구현.
- core/api test 구현.
- WebSocket 계약 문서화.
- `docs/last-구현.md` Section 4-5 정합성 반영.

이번 이슈에서 제외:

- Custom Game start API.
- `ROOM_STARTED` event.
- `GameRoom.gameMode=CUSTOM` 생성.
- scenario 생성.
- Custom GamePlay handoff.
- custom game result WebSocket 처리.
- 랭크 제외/전적 기록 정책 구현.
- 프론트 CustomRoomPage 구현.
- 프론트 WebSocket 연결 구현.
- Redis/pub-sub 기반 멀티 인스턴스 broadcast.
- disconnect timeout leave 정책.
- room 만료 scheduler.
- 새 외부 패키지 추가.

## 📚 Tasks

### 1. Backend Contract 정리

- [x] endpoint를 `/ws/custom-games/rooms/{roomId}?token={accessToken}`로 확정.
- [x] token query parameter 인증 정책을 기존 Game WebSocket과 맞춤.
- [x] `ROOM_UPDATED`, `ROOM_CLOSED`, `ERROR` payload shape 확정.
- [x] disconnect/error는 DB leave가 아니라 session 제거임을 문서화.
- [x] HTTP command와 WebSocket event 책임 분리 문서화.
- [x] `ROOM_STARTED`는 후속 4-6 범위임을 문서화.

### 2. Core Participant 검증 구현

- [x] `CustomGameRoomReadService.validateWaitingParticipant(roomId, userId)` 구현.
- [x] roomId/userId null 검증.
- [x] room 존재 여부 검증.
- [x] room이 `WAITING`인지 검증.
- [x] user가 participant인지 검증.
- [x] 검증 실패 시 기존 `CoreErrorCode`를 사용.
- [x] API 모듈이 custom room repository를 직접 참조하지 않도록 유지.

### 3. WebSocket Handshake 구현

- [x] `CustomRoomWebSocketPathResolver` 구현.
- [x] `CustomRoomWebSocketHandshakeInterceptor` 구현.
- [x] `JwtTokenResolver.resolveWebSocketToken()` 재사용.
- [x] `JwtTokenProvider.validateToken()` / `getUserId()` 재사용.
- [x] core read service로 participant 접근 검증.
- [x] session attributes에 `customRoomId`, `userId` 저장.
- [x] 인증 실패는 `401`, path 실패는 `400`, participant 검증 실패는 `403`으로 처리.

### 4. Session Registry 구현

- [x] `CustomRoomWebSocketSessionAttribute` 구현.
- [x] `CustomRoomWebSocketSession` 구현.
- [x] `CustomRoomWebSocketSessionRegistry` 구현.
- [x] roomId/userId/sessionId 기준 등록 구현.
- [x] 같은 room/user 재연결 시 기존 session close 후 교체.
- [x] sessionId 기준 unregister 구현.
- [x] roomId 기준 session 목록 조회 구현.
- [x] userId 기준 session close/unregister 구현.
- [x] roomId 기준 전체 session close/unregister 구현.
- [x] disconnect/error는 DB 상태를 바꾸지 않음.

### 5. Message DTO / Sender 구현

- [ ] `CustomRoomWebSocketMessageType` 구현.
- [ ] `CustomRoomWebSocketServerMessage` 구현.
- [ ] `ROOM_UPDATED` factory 구현.
- [ ] `ROOM_CLOSED` factory 구현.
- [ ] `ERROR` factory 구현.
- [ ] `CustomRoomWebSocketMessageSender` 구현.
- [ ] open session에만 message 전송.
- [ ] 전송 실패 시 로그만 남기고 HTTP command 결과를 되돌리지 않음.

### 6. Handler / Service 구현

- [ ] `CustomRoomWebSocketHandler` 구현.
- [ ] 연결 성공 시 registry 등록.
- [ ] 연결 성공 시 현재 room snapshot을 해당 session에 `ROOM_UPDATED`로 전송.
- [ ] client text message 수신 시 `ERROR` 응답 후 연결 유지.
- [ ] close/error 시 registry에서만 제거.
- [ ] `CustomRoomWebSocketConfig`에 endpoint 등록.
- [ ] 기존 Game WebSocket endpoint와 충돌 없게 구성.

### 7. Join / Leave Broadcast 연결

- [ ] `CustomRoomWebSocketNotifier` 구현.
- [ ] transaction active 시 afterCommit broadcast 예약.
- [ ] transaction inactive 시 즉시 broadcast.
- [ ] join 성공 후 `ROOM_UPDATED` broadcast 연결.
- [ ] 일반 참가자 leave 성공 후 `ROOM_UPDATED` broadcast 연결.
- [ ] 일반 참가자 leave 호출 user session close/unregister 연결.
- [ ] 방장 leave 성공 후 `ROOM_CLOSED` broadcast 연결.
- [ ] 방장 leave 이후 room sessions close/unregister 연결.
- [ ] command 실패 시 broadcast 미호출 검증.

### 8. Test 구현

- [ ] core read service unit test 작성.
- [ ] core read service `@DataJpaTest` 또는 기존 repository 기반 검증 보강.
- [ ] path resolver unit test 작성.
- [ ] handshake interceptor unit test 작성.
- [ ] session registry unit test 작성.
- [ ] message DTO unit test 작성.
- [ ] message sender unit test 작성.
- [ ] handler unit test 작성.
- [ ] notifier unit test 작성.
- [ ] API service join/leave broadcast 연동 test 작성.
- [ ] disconnect/error가 DB leave를 호출하지 않는지 검증.

### 9. 문서 정합성 구현

- [ ] `docs/last-구현.md` Section 4-5 endpoint와 정책 반영.
- [ ] issue-126과 HTTP/WebSocket 책임이 충돌하지 않는지 확인.
- [ ] 후속 4-6 `ROOM_STARTED` 범위와 충돌하지 않는지 확인.
- [ ] issue-128 PR 섹션 보강.

### 10. 검증

- [ ] `./gradlew :league-of-star-core:test`
- [ ] `./gradlew :league-of-star-api:test`
- [ ] `./gradlew test`
- [ ] `./gradlew build`
- [ ] `git diff --check`

## Implementation Policy

- core 모듈은 custom room/participant DB 조회와 도메인 검증만 담당한다.
- api 모듈은 WebSocket 인증, session registry, message 전송, broadcast 연결을 담당한다.
- API 모듈은 custom room repository를 직접 참조하지 않는다.
- Custom Room WebSocket은 Game WebSocket과 분리한다.
- Custom Room WebSocket session registry는 local memory 기반으로 구현한다.
- disconnect/error는 DB leave가 아니다.
- 실제 leave는 HTTP leave API만 수행한다.
- join/leave broadcast는 transaction commit 이후 수행한다.
- event payload는 기존 `CustomRoomResponse`를 재사용한다.
- `ROOM_STARTED`는 이번 이슈에서 구현하지 않는다.
- 새 외부 패키지를 추가하지 않는다.

## Acceptance Criteria

- room participant만 Custom Room WebSocket에 연결할 수 있다.
- participant가 아닌 사용자는 handshake에서 거부된다.
- 연결 성공 시 현재 room state가 `ROOM_UPDATED`로 전송된다.
- join 성공 후 같은 room 참가자에게 `ROOM_UPDATED`가 broadcast된다.
- 일반 참가자 leave 성공 후 남은 참가자에게 `ROOM_UPDATED`가 broadcast된다.
- 방장 leave 성공 후 room 참가자에게 `ROOM_CLOSED`가 broadcast되고 session이 정리된다.
- WebSocket disconnect/error는 participant DB row를 삭제하지 않는다.
- HTTP join/leave 실패 시 WebSocket event가 broadcast되지 않는다.
- core/api 테스트가 통과한다.
- issue-126, 4-6 문서와 범위가 겹치지 않는다.

## PR Message

## PR 작성 방법

## 📌 Summary

사용자 지정 방 대기실의 실시간 동기화를 위한 Custom Room WebSocket을 구현함.

이번 PR은 방에 들어가고 나가는 HTTP command를 대체하지 않는다. HTTP command가 DB 상태를 바꾸고, WebSocket은 그 결과를 같은 방 참가자 화면에 전달한다.

```mermaid
flowchart TD
    A["join/leave HTTP command"] --> B["DB room/participant 변경"]
    B --> C["CustomRoomResponse 조립"]
    C --> D["HTTP 응답"]
    C --> E["afterCommit WebSocket broadcast"]
    E --> F{"room status"}
    F -->|WAITING| G["ROOM_UPDATED"]
    F -->|CLOSED| H["ROOM_CLOSED + sessions close"]
```

핵심 정책:

- WebSocket 연결은 DB 참가 상태를 만들지 않음.
- 실제 참가/나가기는 HTTP command만 수행함.
- disconnect/error는 session 제거만 수행하고 DB leave로 처리하지 않음.
- 다른 참가자 화면 반영은 `ROOM_UPDATED`, `ROOM_CLOSED` event 기준으로 처리함.
- Custom Room WebSocket은 Game WebSocket과 분리함.

백엔드와의 구현 계약:

- WebSocket endpoint는 `/ws/custom-games/rooms/{roomId}?token={accessToken}`임.
- token은 query parameter로 전달함.
- handshake에서 core read service로 room participant 여부를 검증함.
- event payload는 `CustomRoomResponse`를 재사용함.
- `ROOM_STARTED`는 후속 4-6에서 구현함.

## 📚 Changes

- HTTP command와 WebSocket event 책임을 분리함.
  join/leave HTTP API는 DB 상태 변경 source이고, WebSocket은 다른 참가자에게 최신 상태를 알리는 채널이다. 이 둘을 섞지 않아야 새로고침이나 연결 끊김이 의도치 않은 leave로 처리되지 않는다.

- disconnect를 leave로 처리하지 않음.
  브라우저 새로고침, 모바일 네트워크 흔들림, 탭 일시 중단은 모두 WebSocket disconnect로 보일 수 있다. 이때 participant row를 삭제하면 사용자가 의도하지 않았는데 방에서 나가게 된다. 그래서 disconnect는 session registry에서만 제거하고, 실제 나가기는 leave API로만 처리한다.

- core/api 책임을 분리함.
  room participant 검증은 core read service에서 수행한다. api 모듈은 WebSocket handshake, session 관리, message 전송만 담당한다. 이렇게 해야 start API와 후속 WebSocket 이슈에서도 같은 participant 검증 정책을 재사용할 수 있다.

- afterCommit broadcast로 커밋 전 이벤트 전송을 막음.
  DB 변경이 rollback되었는데 WebSocket event만 먼저 나가면 화면과 DB가 어긋난다. join/leave 성공 이벤트는 transaction commit 이후에 전송하도록 해서 클라이언트가 받은 event가 실제 DB 상태와 맞게 한다.

- Custom Room WebSocket을 Game WebSocket과 분리함.
  Game WebSocket은 READY, RTT, LIGHTNING, GAME_RESULT 같은 게임 진행 상태를 다룬다. Custom Room WebSocket은 대기실 참가자 목록과 room lifecycle만 다룬다. 역할이 다르므로 endpoint, DTO, registry를 분리해 후속 게임 시작 로직과 섞이지 않게 한다.

## 📝 Note

- 이번 PR에서 `ROOM_STARTED`는 제외함.
- Custom Game start API와 scenario 생성은 후속 4-6 범위임.
- 프론트 CustomRoomPage WebSocket 연결은 후속 프론트 이슈 범위임.
- Redis/pub-sub 기반 멀티 인스턴스 broadcast는 제외함.
- 새 패키지 추가 없음.
- 검증 결과를 PR 작성 시 기록함.

## 📌 Related Issue

- Closes #128
