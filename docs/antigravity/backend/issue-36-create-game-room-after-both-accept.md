# Issue 36. Create Game Room After Both Accept

## 📌 Feature Description

두 명의 플레이어가 모두 매칭을 수락하면 서버가 먼저 gameRoom을 생성하고, 게임 대기 화면 진입에 필요한 값을 `match_response_result.game` payload로 전달한다.

현재는 양쪽 수락 시 `GO_TO_GAME_WAITING`이 발행되지만 `game=null`이다.
이번 이슈에서는 양쪽 수락 완료 시점과 게임 세션 생성의 연결 지점을 만든다.

정상 흐름:

```text
양쪽 accept
-> gameRoom 생성
-> game_participants 2명 생성
-> HP scenario 생성/저장
-> Redis user status = IN_GAME
-> match_response_result
   outcome=MATCHED
   reason=BOTH_ACCEPTED
   action=GO_TO_GAME_WAITING
   game={gameRoomId, videoUrl, webSocketUrl}
```

gameRoom 생성 실패 흐름:

```text
양쪽 accept
-> gameRoom/scenario 생성 실패
-> Redis user status 제거
-> match_response_result
   outcome=FAILED
   reason=GAME_SETUP_FAILED
   action=GO_TO_MATCH_START
   game=null
```

gameRoom 생성 실패는 두 유저를 매칭 큐에 자동 복귀시키지 않는다.
클라이언트는 `reason=GAME_SETUP_FAILED`를 기준으로 안내 메시지를 보여준 뒤 start 버튼 화면으로 돌려보낸다.

이번 이슈에서는 WebSocket 연결, RTT 측정, countdown, `GAME_START`, SMITE 판정, `game_actions`, `game_records` 저장은 구현하지 않는다.

## 📚 Tasks

### 1. 정책/이벤트 모델 정리

- [x] `GAME_SETUP_FAILED` 실패 reason 추가 위치 확인
- [x] gameRoom 생성 실패 시 action을 `GO_TO_MATCH_START`로 결정
- [x] gameRoom 생성 실패 시 두 유저를 큐에 재삽입하지 않는 정책 반영
- [x] 클라이언트는 `GAME_SETUP_FAILED` reason 기준으로 안내 문구를 매핑하기로 결정
- [x] `match_response_result.game` 성공 payload 필드 확정
  - [x] `gameRoomId`
  - [x] `videoUrl`
  - [x] `webSocketUrl`

결정 사항:

- `MatchResponseReason.GAME_SETUP_FAILED`를 추가한다.
- gameRoom 생성 실패 이벤트는 `FAILED / GAME_SETUP_FAILED / GO_TO_MATCH_START`로 내려간다.
- gameRoom 생성 실패 이벤트의 `game`은 `null`이다.
- gameRoom 생성 실패 이벤트에 별도 `message` 필드는 추가하지 않는다.
- 클라이언트는 `reason=GAME_SETUP_FAILED`를 보고 "게임 준비 중 문제가 발생했습니다. 다시 매칭을 시도해 주세요." 문구를 표시한다.
- 성공 이벤트의 `game` payload는 `gameRoomId`, `videoUrl`, `webSocketUrl`을 포함한다.
- `MatchStatus.GAME_SETUP_FAILED`를 추가해 `DECLINED`, `TIMEOUT`과 구분한다.

### 2. gameRoom 생성 유스케이스 추가

- [x] 양쪽 수락 완료 후 호출할 gameRoom 생성 서비스 정의
- [x] 입력값 정의
  - [x] `firstUserId`
  - [x] `secondUserId`
- [x] 반환값 정의
  - [x] `GameRoom`
- [x] game 도메인 관심사가 아닌 값 제거
  - [x] `matchId`
  - [x] `videoUrl`
  - [x] `webSocketUrl`
- [x] 별도 실패 결과 모델 제거

결정 사항:

- 기존 코드 컨벤션에 맞춰 `CreateGameRoomUseCase` 인터페이스 대신 `GameRoomCommandService`를 둔다.
- `smite-core` game 도메인은 `matchId`, SSE payload, URL 조립을 알지 않는다.
- `GameRoomCommandService`는 `GameRoom` 생성, participant 추가, 기본 scenario 생성, 저장까지만 담당한다.
- HP scenario 길이는 gameRoom 생성 시 8초 이상 17초 이하로 랜덤 결정한다.
- HP scenario는 1초 단위 step으로 구성하고, 시작 HP는 10000, 마지막 step HP는 0으로 둔다.
- `match_response_result.game`의 `videoUrl`, `webSocketUrl`은 이후 매칭/알림 연결 단계에서 조립한다.
- gameRoom 생성 실패는 별도 result 타입이 아니라 `CoreException` 계열 예외로 전파하고, 호출부에서 `GAME_SETUP_FAILED` 이벤트로 변환한다.
- 잘못된 참가자 입력은 `CoreErrorCode.INVALID_GAME_PARTICIPANTS`로 표현한다.

### 3. gameRoom 저장 구현

- [x] `GameRoom` 생성 로직 연결
- [x] `game_rooms.status=READY`로 저장
- [x] `game_participants` 2명 추가
- [x] `game_participants.status=READY`로 저장
- [x] HP scenario 생성
- [x] `scenario_data` 저장
- [x] 고정 MP4 URL 반환
- [x] game WebSocket URL 반환

결정 사항:

- `GameRoomCommandService`는 DB 저장만 담당한다.
- `GameRoomSetupService`는 `smite-api`의 game service에 둔다.
- `smite-matching`은 gameRoom 생성, MP4 URL, WebSocket URL 조립을 알지 않는다.
- 고정 MP4 URL은 `/assets/game/dragon-view.mp4`로 반환한다.
- MP4 파일은 아직 프로젝트에 없지만, Spring Boot static resource 경로에 존재한다고 가정한다.
- game WebSocket URL은 `/ws/game/{gameRoomId}` 형식으로 반환한다.

### 4. 매칭 성공 흐름과 연결

- [ ] `MatchResponseResultService` 양쪽 accept 완료 지점 확인
- [ ] 기존 양쪽 accept 완료 흐름에 gameRoom 생성 호출 추가
- [ ] gameRoom 생성 성공 후 match session을 `ACCEPTED`로 저장
- [ ] gameRoom 생성 성공 후 timeout index cleanup 유지
- [ ] gameRoom 생성 성공 후 두 유저 Redis status를 `IN_GAME`으로 전환
- [ ] gameRoom 생성 성공 후 `match_response_result` 발행

### 5. `match_response_result.game` payload 채우기

- [ ] matching 내부 이벤트에 game payload에 필요한 내부 결과 추가
- [ ] notification factory에서 성공 이벤트의 `game`을 `null`이 아니게 생성
- [ ] `gameRoomId` 매핑
- [ ] `videoUrl` 매핑
- [ ] `webSocketUrl` 매핑
- [ ] 실패 이벤트에서는 `game=null` 유지

### 6. gameRoom 생성 실패 처리

- [ ] gameRoom 생성 실패를 잡아 매칭 성공 이벤트와 분리
- [ ] 두 유저 Redis status 제거
- [ ] 매칭 큐 재삽입 없음
- [ ] `GAME_SETUP_FAILED` reason으로 실패 이벤트 발행
- [ ] action은 `GO_TO_MATCH_START`
- [ ] `game=null`
- [ ] 로그/메트릭 기록

### 7. 테스트

- [ ] 양쪽 accept 시 gameRoom이 생성되는지 테스트
- [ ] gameRoom participant가 2명 생성되는지 테스트
- [ ] scenario가 저장되는지 테스트
- [ ] 성공 이벤트에 `gameRoomId`, `videoUrl`, `webSocketUrl`이 포함되는지 테스트
- [ ] 성공 후 두 유저 Redis status가 `IN_GAME`인지 테스트
- [ ] gameRoom 생성 실패 시 두 유저 Redis status가 제거되는지 테스트
- [ ] gameRoom 생성 실패 시 큐에 재삽입하지 않는지 테스트
- [ ] gameRoom 생성 실패 이벤트가 `FAILED / GAME_SETUP_FAILED / GO_TO_MATCH_START`인지 테스트
- [ ] 기존 reject/timeout 정산 테스트가 깨지지 않는지 확인

### 8. 문서

- [ ] issue-34 `match_response_result` 문서에 `GAME_SETUP_FAILED` reason 추가
- [ ] `GO_TO_GAME_WAITING`은 gameRoom 생성 성공 후에만 발행된다고 명시
- [ ] gameRoom 생성 실패 시 큐 복귀하지 않고 start 화면으로 복귀한다고 명시
- [ ] `match_response_result.game` 성공 payload 예시 추가
- [ ] OpenAPI/RestDocs/SSE 문서 갱신

## ✅ 완료 기준

- 양쪽 accept 성공 시 gameRoom이 생성된다.
- `match_response_result.action=GO_TO_GAME_WAITING`은 gameRoom 생성 성공 후에만 발행된다.
- 성공 이벤트의 `game` payload가 `null`이 아니다.
- 성공 후 두 유저 Redis status는 `IN_GAME`이다.
- gameRoom 생성 실패 시 두 유저는 큐에 자동 복귀하지 않는다.
- gameRoom 생성 실패 시 두 유저는 start 버튼 화면으로 돌아갈 수 있는 실패 이벤트를 받는다.
- WebSocket/RTT/SMITE/game_records는 이번 이슈에서 구현하지 않는다.

## 변경 이력

| 날짜 | 변경 내용 |
| :--- | :--- |
| 2026-05-13 | Issue 36 작업 문서 생성. 양쪽 accept 후 gameRoom 생성, 성공 payload, 실패 시 `GAME_SETUP_FAILED / GO_TO_MATCH_START` 정책 정리 |
