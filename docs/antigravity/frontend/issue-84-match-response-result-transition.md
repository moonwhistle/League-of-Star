# Issue 84. Match Response Result Transition Implementation

## Feature Description

Vue 프론트엔드의 `/match` 페이지에서 SSE `match_response_result` 최종 이벤트를 기준으로 action별 화면 전환을 구현한다.

이번 이슈는 `docs/antigravity/frontend/front-plan.md`의 `7. [ ] 매칭 응답 결과 화면 전환 구현`을 구현 기준으로 삼는다. Issue 82에서 accept/reject HTTP command는 연결됐지만, 최종 화면 전환은 아직 처리하지 않는다. 이번 이슈에서는 `match_response_result.action`을 받아 게임 대기 화면 이동, 매칭 시작 가능 상태 복귀, 매칭 대기 상태 복귀를 구현한다.

핵심은 action별 SSE 연결 정책을 분리하는 것이다. 백엔드 코드 확인 결과 `RETURN_TO_MATCHING`은 수락 유저를 기존 `entryTime/tierScore`로 큐에 다시 넣고 `userStatus=MATCHING`으로 바꾼 뒤 발행된다. 따라서 프론트는 `RETURN_TO_MATCHING`에서 join/leave API를 호출하지 않고, SSE 연결을 유지한 채 queued 상태를 표시한다.

```mermaid
flowchart TD
    A["SSE match_response_result 수신"] --> B{"action"}
    B -->|GO_TO_GAME_WAITING| C{"game payload 유효?"}
    C -->|yes| D["game waiting payload 저장"]
    D --> E["매칭 SSE close"]
    E --> F["/game/{gameRoomId}/waiting 이동"]
    C -->|no| G["에러 표시"]
    G --> H["ready 복귀<br/>SSE close"]
    B -->|GO_TO_MATCH_START| I["모달/command 상태 정리"]
    I --> J["ready 복귀<br/>SSE close"]
    B -->|RETURN_TO_MATCHING| K["모달/command 상태 정리"]
    K --> L["queued 복귀<br/>대기 타이머 재시작"]
    L --> M["SSE 유지<br/>다음 match_found 대기"]
```

## Backend Contract

| 항목                | 기준                           |
| ------------------- | ------------------------------ |
| Final event         | SSE `match_response_result`    |
| Transition source   | `match_response_result.action` |
| Game waiting action | `GO_TO_GAME_WAITING`           |
| Start screen action | `GO_TO_MATCH_START`            |
| Queue return action | `RETURN_TO_MATCHING`           |
| Game waiting route  | `/game/:gameRoomId/waiting`    |

`match_response_result` payload:

```ts
interface MatchResponseResultNotification {
  matchId: string;
  outcome: "MATCHED" | "FAILED";
  reason:
    | "BOTH_ACCEPTED"
    | "MY_REJECTED"
    | "OPPONENT_REJECTED"
    | "MY_TIMEOUT"
    | "OPPONENT_TIMEOUT"
    | "BOTH_TIMEOUT"
    | "GAME_SETUP_FAILED";
  action: "GO_TO_GAME_WAITING" | "GO_TO_MATCH_START" | "RETURN_TO_MATCHING";
  opponent: {
    userId: number;
    nickname: string;
    tier: string;
    tierScore: number;
  } | null;
  game: {
    gameRoomId: number;
    videoUrl: string;
    webSocketUrl: string;
  } | null;
}
```

프론트 처리 기준:

- `GO_TO_GAME_WAITING`은 `game.gameRoomId`, `game.videoUrl`, `game.webSocketUrl`이 있어야 게임 대기 화면으로 이동한다.
- `GO_TO_GAME_WAITING` 이동 전 `matchId`, `opponent`, `game`, `receivedAt`을 `sessionStorage`에 저장한다.
- `GO_TO_GAME_WAITING`은 매칭 SSE를 닫고 게임 대기 화면으로 이동한다.
- `GO_TO_MATCH_START`는 매칭 SSE를 닫고 ready 상태로 복귀한다.
- `RETURN_TO_MATCHING`은 백엔드가 이미 큐 복귀를 완료한 상태로 보고 queued 상태로 복귀한다.
- `RETURN_TO_MATCHING`은 매칭 SSE를 닫지 않는다.
- `RETURN_TO_MATCHING`에서는 `joinMatchQueue`, `leaveMatchQueue`를 호출하지 않는다.
- `GO_TO_GAME_WAITING`, `GO_TO_MATCH_START` 처리 이후 늦게 도착한 매칭 SSE callback은 무시한다.

## Scope Boundary

이번 이슈에 포함한다.

- `match_response_result.action` 기반 화면 전환 구현.
- `GO_TO_GAME_WAITING` route 이동 구현.
- `GO_TO_MATCH_START` ready 복귀 구현.
- `RETURN_TO_MATCHING` queued 복귀 구현.
- action별 매칭 SSE close / 유지 정책 구현.
- game waiting payload `sessionStorage` 저장/조회 구현.
- `GameWaitingPage.vue` loading UI 구현.
- `background-new-sharp.png` 기반 game waiting background 구현.
- `gameloading.png` 참고 디자인 기반 CSS 재현.
- game waiting loading bar payload 확보율 구현.
- game waiting loading bar 문구는 게임 시작 준비 완료가 아니라 payload 수신 상태로 표시.
- game waiting 화면에는 사용자에게 필요한 상대 정보와 준비 상태만 표시하고, `gameRoomId`, `matchId`는 내부 저장/검증 데이터로만 유지.
- 한/영 locale 문구 추가.
- Match page / Game waiting page 테스트 구현.
- 문서 정합성 반영.
- lint / format / typecheck / test / build 검증.

이번 이슈에서 제외한다.

- game waiting WebSocket 연결 구현.
- `CLIENT_READY` 전송 구현.
- `GAME_WAITING_TIMEOUT`, `GAME_START_FAILED`, `COUNTDOWN`, `GAME_START` 처리 구현.
- game play / game result route 전환 구현.
- match session 재조회 API 구현.
- watchdog/session recovery 구현.
- Pinia 또는 전역 match store 도입.
- 새 패키지 추가.

## Tasks

### 1. 백엔드 match_response_result 계약 반영

- [x] `match_response_result` payload shape 확인.
- [x] `GO_TO_GAME_WAITING` action 정책 반영.
- [x] `GO_TO_MATCH_START` action 정책 반영.
- [x] `RETURN_TO_MATCHING` action 정책 반영.
- [x] `RETURN_TO_MATCHING`은 백엔드 큐 복귀 완료 후 발행된다는 정책 반영.
- [x] `GO_TO_GAME_WAITING`에서 필요한 `game` 필수 payload 기준 반영.
- [x] 실패 이벤트에서 `game === null`일 수 있다는 정책 반영.

### 2. Match page result transition 구현

- [x] `onMatchResponseResult`에서 action별 handler 분기 구현.
- [x] result 수신 시 match found modal 닫기 구현.
- [x] result 수신 시 countdown timer 정리 구현.
- [x] result 수신 시 accept/reject command state 초기화 구현.
- [x] `GO_TO_GAME_WAITING` route 이동 구현.
- [x] `GO_TO_GAME_WAITING` 처리 시 매칭 SSE close 구현.
- [x] `GO_TO_MATCH_START` ready 복귀 구현.
- [x] `GO_TO_MATCH_START` 처리 시 매칭 SSE close 구현.
- [x] `RETURN_TO_MATCHING` queued 복귀 구현.
- [x] `RETURN_TO_MATCHING` 처리 시 매칭 SSE 유지 구현.
- [x] `RETURN_TO_MATCHING` 처리 시 waiting timer 재시작 구현.
- [x] `RETURN_TO_MATCHING` 처리 시 join/leave API 미호출 유지 구현.
- [x] `GO_TO_GAME_WAITING`, `GO_TO_MATCH_START` 이후 늦은 매칭 SSE callback 무시 구현.
- [x] `game` 필수 payload 누락 시 game waiting 이동 금지 구현.

### 3. Game waiting payload storage 구현

- [x] `sessionStorage` 저장 helper 구현.
- [x] `sessionStorage` 조회 helper 구현.
- [x] 저장 key를 `league-of-star.gameWaitingPayload:{gameRoomId}` 기준으로 구현하고, League of Star 계약 전환 이후 기존 namespace 읽기 fallback은 제거.
- [x] 저장 payload에 `matchId`, `opponent`, `game`, `receivedAt` 포함.
- [x] route param `gameRoomId`와 저장 payload 불일치 시 안전 복귀 구현.
- [x] payload 없음 또는 parse 실패 시 안전 복귀 구현.

### 4. Game waiting UI 구현

- [x] `GameWaitingPage.vue` placeholder 제거.
- [x] `background-new-sharp.png` 기반 full-screen background 구현.
- [x] `gameloading.png` 참고 디자인을 CSS로 재현.
- [x] `gameloading.png` runtime import 금지 유지.
- [x] `LEAGUE OF STAR` title 구현.
- [x] 게임 준비 중 title 구현.
- [x] 상대 nickname / tier 표시 구현.
- [x] `gameRoomId`, `matchId`는 화면 표시 없이 내부 저장/검증 상태로 유지.
- [x] segmented loading bar 구현.
- [x] payload 확보율 기반 loading progress 구현.
- [x] desktop/mobile responsive layout 구현.

### 5. Loading bar 정책 구현

- [x] 필수 payload 항목을 5개로 정의.
  - `matchId`
  - `opponent`
  - `game.gameRoomId`
  - `game.videoUrl`
  - `game.webSocketUrl`
- [x] 각 항목을 20%로 계산.
- [x] 1개 확보 시 20%, 2개 확보 시 40%, 3개 확보 시 60%, 4개 확보 시 80%, 5개 확보 시 100% 표시.
- [x] 정상 `GO_TO_GAME_WAITING` payload는 route 진입 직후 100%가 될 수 있음을 UI 정책에 반영.
- [x] WebSocket 연결률과 `CLIENT_READY` progress는 이번 이슈에서 제외.
- [x] `100%` 문구는 게임 시작 준비 완료가 아니라 payload 수신 완료로 표시.

### 6. Locale 구현

- [x] match result 실패 안내 문구 추가.
- [x] game waiting title 문구 추가.
- [x] game waiting status 문구 추가.
- [x] game waiting payload missing 문구 추가.
- [x] loading step 문구 추가.
- [x] 한/영 전환 시 game waiting 문구 갱신 구현.

### 7. Test 구현

- [x] `GO_TO_GAME_WAITING` 수신 시 route 이동 검증.
- [x] `GO_TO_GAME_WAITING` 수신 시 match SSE close 검증.
- [x] `GO_TO_GAME_WAITING` 수신 시 payload 저장 검증.
- [x] `GO_TO_GAME_WAITING` payload 누락 시 route 이동 금지 검증.
- [x] `GO_TO_MATCH_START` 수신 시 ready 복귀 검증.
- [x] `GO_TO_MATCH_START` 수신 시 match SSE close 검증.
- [x] `RETURN_TO_MATCHING` 수신 시 queued 복귀 검증.
- [x] `RETURN_TO_MATCHING` 수신 시 match SSE close 미호출 검증.
- [x] `RETURN_TO_MATCHING` 수신 시 join/leave API 미호출 검증.
- [x] `RETURN_TO_MATCHING` 이후 다음 `match_found` 수신 가능 검증.
- [x] `GO_TO_GAME_WAITING` 이후 늦은 매칭 SSE callback 무시 검증.
- [x] Game waiting page 상대 정보 표시 및 payload 기반 progress 검증.
- [x] Game waiting loading bar가 `Ready`가 아니라 payload 수신 상태로 표시되는지 검증.
- [x] loading bar 20/40/60/80/100% 계산 검증.
- [x] payload 없음 또는 route param 불일치 시 `/match` 복귀 검증.
- [x] locale toggle 시 game waiting 문구 전환 검증.

### 8. 문서 정합성 구현

- [x] `front-plan.md` 7번 단계와 issue-84 범위 정합성 확인.
- [x] issue-82에서 제외한 `match_response_result.action` 전환을 issue-84에서 구현한다는 연결 확인.
- [x] backend issue-34의 action mapping과 프론트 처리 정책 정합성 확인.
- [x] project policy의 SSE close 정책과 action별 처리 정책 정합성 확인.
- [x] “`match_response_result` 수신 후 항상 SSE close” 표현이 남아 있으면 action별 정책으로 수정.
- [x] `RETURN_TO_MATCHING`은 SSE 유지 + queued 복귀 정책으로 문서화.
- [x] `gameloading.png`는 참고 디자인이며 runtime import하지 않는 정책 문서화.
- [x] loading bar는 필수 payload 확보율 기준이라는 정책 문서화.
- [x] 이번 이슈 PR 메시지 섹션 작성.

### 9. 검증

- [x] `npm run format` 검증.
- [x] `npm run lint` 검증.
- [x] `npm run typecheck` 검증.
- [x] `npm run test` 검증.
- [x] `npm run build` 검증.
- [x] desktop viewport에서 game waiting UI overflow 확인.
- [x] mobile viewport에서 game waiting UI overflow 확인.
- [x] loading bar segment가 desktop/mobile에서 겹치지 않는지 확인.

## Implementation Policy

- 최종 화면 전환 기준은 HTTP accept/reject 응답이 아니라 SSE `match_response_result.action`이다.
- `GO_TO_GAME_WAITING`은 `game` payload가 유효할 때만 route 이동한다.
- `GO_TO_GAME_WAITING`은 match SSE를 닫고 game waiting 화면으로 이동한다.
- `GO_TO_MATCH_START`는 match SSE를 닫고 ready 상태로 복귀한다.
- `RETURN_TO_MATCHING`은 match SSE를 유지하고 queued 상태로 복귀한다.
- `RETURN_TO_MATCHING`은 join/leave API를 호출하지 않는다.
- `RETURN_TO_MATCHING`은 백엔드 큐 복귀 완료 이벤트로 해석한다.
- `GO_TO_GAME_WAITING`, `GO_TO_MATCH_START` 이후에는 최종 전환이 끝난 것으로 보고 늦은 매칭 SSE callback을 무시한다.
- `game` payload는 `sessionStorage`에 최소 저장한다.
- `GameWaitingPage.vue`는 상대 정보 표시와 loading UI까지만 담당한다.
- `GameWaitingPage.vue`는 `gameRoomId`, `matchId`를 사용자에게 노출하지 않고 route 검증, storage key, loading progress 계산에만 사용한다.
- game waiting WebSocket 연결은 후속 이슈에서 구현한다.
- `gameloading.png`는 참고 디자인으로만 사용하고 import하지 않는다.
- 새 패키지를 추가하지 않는다.

## Acceptance Criteria

- `GO_TO_GAME_WAITING` 수신 시 `/game/:gameRoomId/waiting`으로 이동함.
- `GO_TO_GAME_WAITING` 수신 시 game waiting payload가 저장됨.
- `GO_TO_GAME_WAITING` 수신 시 match SSE가 닫힘.
- `GO_TO_GAME_WAITING`인데 `game` 필수 payload가 누락되면 game waiting으로 이동하지 않음.
- `GO_TO_MATCH_START` 수신 시 ready 상태로 복귀함.
- `GO_TO_MATCH_START` 수신 시 match SSE가 닫힘.
- `RETURN_TO_MATCHING` 수신 시 queued 상태로 복귀함.
- `RETURN_TO_MATCHING` 수신 시 match SSE가 유지됨.
- `RETURN_TO_MATCHING` 수신 시 join/leave API를 호출하지 않음.
- Game waiting page는 저장된 opponent 정보를 표시하고, game payload는 내부 검증과 loading progress 계산에 사용함.
- Loading bar는 5개 필수 payload 기준으로 20% 단위로 표시됨.
- Loading bar 100%는 게임 시작 준비 완료가 아니라 필수 payload 수신 완료로 표시됨.
- `gameloading.png`는 runtime asset으로 사용되지 않음.
- lint / format / typecheck / test / build 통과.

## PR Message

## 📌 Summary

`/match` 페이지에서 수락/거절 HTTP 응답이 아니라 SSE `match_response_result.action`을 기준으로 최종 화면을 전환함.

HTTP accept/reject는 “요청 접수”까지만 의미하고, 실제 결과는 백엔드가 Redis/queue/game room 처리를 끝낸 뒤 발행하는 `match_response_result`를 진실의 기준으로 삼음.

```mermaid
flowchart TD
    A["사용자 accept/reject 클릭"] --> B["HTTP command 전송"]
    B --> C["HTTP 200 OK<br/>command ack"]
    C --> D["모달 유지<br/>SSE 결과 대기"]
    D --> E["match_response_result 수신"]
    E --> F{"action"}
    F -->|GO_TO_GAME_WAITING| G["game payload 검증"]
    G --> H["sessionStorage 저장"]
    H --> I["match SSE close"]
    I --> J["/game/{gameRoomId}/waiting 이동"]
    F -->|GO_TO_MATCH_START| K["match SSE close"]
    K --> L["ready 복귀"]
    F -->|RETURN_TO_MATCHING| M["queued 복귀"]
    M --> N["SSE 유지<br/>join/leave 미호출"]
```

핵심 정책:

- HTTP 200 OK는 화면 전환 기준이 아니라 command ack로만 취급함.
- 최종 화면 전환은 백엔드가 발행한 `match_response_result.action`만 보고 결정함.
- `GO_TO_GAME_WAITING`은 양쪽 응답이 게임 생성으로 확정된 상태이므로 payload를 저장하고 match SSE를 닫은 뒤 game waiting으로 이동함.
- `GO_TO_MATCH_START`는 매칭 흐름이 종료된 상태로 보고 match SSE를 닫고 ready로 복귀함.
- `RETURN_TO_MATCHING`은 백엔드가 수락 유저를 기존 우선순위로 큐에 다시 넣은 뒤 보내는 이벤트이므로, 프론트는 rejoin/leave 없이 queued로 복귀하고 SSE를 유지함.
- Game waiting 화면은 상대 정보와 loading UI만 보여주고, `gameRoomId`, `matchId`는 route 검증, storage key, loading progress 계산에만 사용함.
- `GO_TO_GAME_WAITING`, `GO_TO_MATCH_START` 이후 늦게 도착한 매칭 SSE callback은 이미 끝난 흐름의 이벤트로 보고 무시함.

## 📚 Changes

- HTTP와 SSE의 책임을 분리함.
  accept/reject API 응답으로 바로 화면을 바꾸면 서버의 최종 매칭 결과와 프론트 화면이 어긋날 수 있음.
  그래서 HTTP는 “요청을 받았음”까지만 믿고, 실제 화면 전환은 SSE 결과 이벤트에 맡김.

- action별 SSE 연결 정책을 다르게 둠.
  `GO_TO_GAME_WAITING`, `GO_TO_MATCH_START`는 현재 매칭이 끝난 흐름이라 SSE를 닫는 것이 맞음.
  반대로 `RETURN_TO_MATCHING`은 백엔드가 이미 큐 복귀까지 처리한 결과라서 SSE를 끊고 다시 붙을 이유가 없음.
  재연결을 하면 불필요한 API 호출과 이벤트 유실 가능성이 생기므로 기존 SSE를 유지함.

- Game waiting payload의 저장 범위를 제한함.
  후속 WebSocket 이슈에서 필요한 `matchId`, `opponent`, `game`, `receivedAt`만 `sessionStorage`에 저장함.
  이 데이터는 새로고침/route 진입 검증을 위한 임시 상태이며, 영구 저장 데이터로 보지 않음.

- `gameRoomId`, `matchId`를 사용자 화면에서 숨김.
  이 값들은 사용자가 판단해야 할 정보가 아니라 내부 식별자임.
  화면에는 상대 닉네임, 티어, LP, 준비 상태처럼 의미 있는 정보만 보여주고, 내부 식별자는 route 검증과 progress 계산에만 사용함.

- Loading bar 기준을 “필수 payload 확보율”로 정함.
  아직 Game WebSocket 연결은 이번 이슈 범위가 아니므로, 로딩 바를 실제 소켓 준비율처럼 보이면 오해가 생김.
  그래서 `matchId`, `opponent`, `gameRoomId`, `videoUrl`, `webSocketUrl` 5개 항목을 각각 20%로 계산함.
  100% 상태 문구도 `Ready`가 아니라 `Received` 계열로 표현해 게임 시작 가능 상태와 구분함.

- 최종 전환 이후 늦은 SSE callback을 무시함.
  `GO_TO_GAME_WAITING`, `GO_TO_MATCH_START`는 현재 매칭 플로우가 끝난 상태임.
  이 뒤에 네트워크 지연으로 이전 `match_found`나 다른 `match_response_result`가 도착해도 화면을 다시 queued/modal 상태로 되돌리지 않도록 guard를 둠.

- Game waiting UI는 참고 이미지를 그대로 삽입하지 않고 CSS로 재구성함.
  `gameloading.png`는 디자인 참고용이고 runtime asset이 아님.
  실제 화면은 기존 `background-new-sharp.png`를 쓰고, desktop/mobile에서 loading 영역과 segment가 겹치지 않도록 반응형 레이아웃을 조정함.

## 📝 Note

- Game waiting WebSocket 연결은 후속 이슈에서 구현함.
- `CLIENT_READY`, timeout, countdown, game start 처리는 이번 이슈 범위가 아님.
- 문서에 남아 있는 “항상 SSE close” 표현은 action별 정책으로 정리함.
- 새 패키지는 추가하지 않음.
- 검증에서 mobile viewport overflow를 발견했고, Game waiting mobile layout을 함께 보정함.

## 📌 Related Issue

- Closes #84
