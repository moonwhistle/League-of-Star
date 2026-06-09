# Issue 88. Game Start Handling

## Feature Description

Vue 프론트엔드의 `/game/:gameRoomId/waiting` 페이지에서 Game WebSocket `COUNTDOWN`, `GAME_START` 이벤트를 실제 게임 시작 흐름으로 연결한다.


핵심은 `GAME_START`를 실제 게임 시작 데이터의 source of truth로 삼되, 클라이언트가 즉시 게임이 시작됐다고 판단하지 않는 것이다. 서버는 `COUNTDOWN`과 `GAME_START`를 `startAt` 전에 미리 전송하고, 프론트는 `GAME_START` 수신 후 play route로 이동하되 실제 게임 시작 기준은 payload의 `startAt`으로 유지한다.

```mermaid
flowchart TD
    A["/game/{gameRoomId}/waiting<br/>Game WebSocket 연결됨"] --> B["RTT_PING 수신"]
    B --> C["RTT_PONG { seq } 즉시 전송"]
    C --> D["COUNTDOWN 수신"]
    D --> E["startAt 기준 countdown 표시"]
    E --> F["GAME_START 수신"]
    F --> G{"route gameRoomId / startAt 유효?"}
    G -->|no| H["WebSocket 정리"]
    H --> I["/match 복귀"]
    G -->|yes| J["serverTime/startAt/scenario 저장"]
    K --> L["/game/{gameRoomId}/play 이동"]
    L --> M["Play 화면은 startAt 기준으로 대기"]
```


## Backend Contract

| 항목                   | 기준                                               |
| ---------------------- | -------------------------------------------------- |
| Game WebSocket         | `/ws/game/{gameRoomId}?token={accessToken}`        |
| Countdown event        | `COUNTDOWN`                                        |
| Start event            | `GAME_START`                                       |
| Start source of truth  | `GAME_START.payload`                               |
| Start route transition | `GAME_START` 수신 후 `/game/:gameRoomId/play` 이동 |
| Actual start 기준      | `GAME_START.payload.startAt`                       |
| Clock 보정             | 이번 이슈에서 하지 않음                            |

`COUNTDOWN` payload:

```ts
interface CountdownPayload {
  gameRoomId: number;
  serverTime: number;
  startAt: number;
  countdownDisplaySeconds: number;
}
```

`GAME_START` payload:

```ts
interface GameStartPayload {
  gameRoomId: number;
  serverTime: number;
  startAt: number;
  scenario: {
    starCoreMaxHp: number;
    durationMs: number;
    hpTimeline: {
      timeMs: number;
      hp: number;
    }[];
  };
}
```

저장할 game start payload:

```ts
interface StoredGameStartPayload {
  gameRoomId: number;
  serverTime: number;
  startAt: number;
  scenario: {
    starCoreMaxHp: number;
    durationMs: number;
    hpTimeline: {
      timeMs: number;
      hp: number;
    }[];
  };
  receivedAt: string;
}
```

프론트 처리 기준:

- `COUNTDOWN`은 시작 예고 이벤트다.
- `COUNTDOWN` 수신 시 `startAt`까지 남은 시간을 계산하고, 남은 시간이 `countdownDisplaySeconds` 이하이면 countdown 숫자를 표시한다.
- 클라이언트가 메시지를 늦게 받으면 `3`이 아니라 `2` 또는 `1`부터 표시할 수 있다.
- `GAME_START`는 실제 게임 시작 데이터 이벤트다.
- `GAME_START` payload의 `serverTime`, `startAt`, `scenario`를 `sessionStorage`에 저장한다.
- `GAME_START` 수신 후 `/game/:gameRoomId/play`로 이동한다.
- `COUNTDOWN`과 `GAME_START`를 모두 수신한 경우 두 이벤트의 `startAt`은 같아야 한다.
- `COUNTDOWN` 없이 `GAME_START`가 먼저 오면 `GAME_START` payload를 source of truth로 삼아 저장하고 play로 이동한다.
- route param `gameRoomId`와 `GAME_START.payload.gameRoomId`가 다르면 잘못된 메시지로 보고 play 이동하지 않는다.
- `GAME_START` 수신 후 play route로 이동해도 실제 시작 기준은 `startAt`이다.
- server/client clock 보정은 후속 고도화로 보류하고, 현재 단계에서는 백엔드가 내려준 `serverTime`, `startAt`을 그대로 사용한다.

## Scope Boundary

이번 이슈에 포함한다.

- `COUNTDOWN` 수신 시 countdown 상태 표시 구현.
- `COUNTDOWN` payload의 `gameRoomId`, `serverTime`, `startAt`, `countdownDisplaySeconds` 반영.
- `GAME_START` 수신 시 `serverTime`, `startAt`, `scenario` 저장 구현.
- game start payload `sessionStorage` 저장/조회 helper 구현.
- 저장 key는 `league-of-star.gameStartPayload:{gameRoomId}`만 사용한다. League of Star 계약 전환 이후 기존 namespace fallback은 유지하지 않는다.
- route param `gameRoomId`와 `GAME_START.payload.gameRoomId` 불일치 시 play 이동 금지 구현.
- `COUNTDOWN.startAt`과 `GAME_START.startAt` 불일치 시 play 이동 금지 구현.
- `GAME_START` 수신 후 `/game/:gameRoomId/play` 이동 구현.
- `GAME_START` 이후 늦은 WebSocket close/error callback이 waiting 상태를 다시 흔들지 않도록 guard 구현.
- `/game/:gameRoomId/play`에서 저장된 game start payload를 읽고 최소 시작 데이터 상태를 표시하는 stub 구현.
- Game Waiting / Game Play / storage helper 테스트 구현.
- 한/영 locale 문구 추가.
- 문서 정합성 반영.
- lint / format / typecheck / test / build 검증.
- desktop/mobile overflow 확인.

이번 이슈에서 제외한다.

- HP bar, countdown, lightning button HUD 구현.
- `requestAnimationFrame` 기반 HP 표시 구현.
- LIGHTNING 클릭 시 `{ type: 'LIGHTNING', payload: null }` 전송 구현.
- `ERROR` 수신 시 play 화면 내 message 표시 구현.
- `GAME_RESULT` 수신 후 result route 이동 구현.
- Game summary API 호출 구현.
- server/client clock skew 보정 구현.
- WebSocket 재접속/복구 구현.
- Pinia 또는 전역 game store 도입.
- 새 패키지 추가.

## Tasks

### 1. 백엔드 GAME_START 계약 반영

- [x] backend issue-46의 `COUNTDOWN`, `GAME_START`, `startAt = serverNow + 4000ms` 정책 확인.
- [x] `docs/project/policy.md`의 GAME_START 시작 동기화 정책 확인.
- [x] `docs/project/websocket client.md`의 `COUNTDOWN`, `GAME_START` 클라이언트 처리 기준 확인.
- [x] `COUNTDOWN` payload shape가 프론트 타입에 이미 반영되어 있는지 확인.
- [x] `GAME_START` payload shape가 프론트 타입에 이미 반영되어 있는지 확인.
- [x] `COUNTDOWN`과 `GAME_START`가 같은 `startAt`을 써야 한다는 정책 문서 반영.
- [x] `GAME_START` 수신 후 play route 이동 정책 문서 반영.
- [x] 실제 시작 기준은 route 이동 시점이 아니라 `GAME_START.payload.startAt`이라는 정책 문서 반영.
- [x] `COUNTDOWN` payload를 page handler에서 countdown 상태에 사용하도록 구현.
- [x] `GAME_START` payload를 storage helper에 저장하도록 구현.

### 2. Game start payload storage 구현

- [x] `sessionStorage` 저장 helper 구현.
- [x] `sessionStorage` 조회 helper 구현.
- [x] 저장 key를 `league-of-star.gameStartPayload:{gameRoomId}` 기준으로 구현하고, 기존 namespace 읽기 fallback은 제거.
- [x] 저장 payload에 `gameRoomId`, `serverTime`, `startAt`, `scenario`, `receivedAt` 포함.
- [x] route param `gameRoomId`와 저장 payload 불일치 시 `null` 반환 구현.
- [x] malformed JSON, payload shape 불일치, 빈 scenario 필수 값 누락 시 `null` 반환 구현.
- [x] `scenario.hpTimeline`은 `timeMs`, `hp` 숫자 배열로 검증.
- [x] storage 접근 불가능 환경에서는 안전하게 no-op 또는 `null` 반환.

### 3. GameWaitingPage countdown 처리 구현

- [x] WebSocket 상태 모델에 countdown 표시 상태 추가.
- [x] `COUNTDOWN` 수신 시 `gameRoomId`, `serverTime`, `startAt`, `countdownDisplaySeconds` 저장.
- [x] route param `gameRoomId`와 `COUNTDOWN.payload.gameRoomId` 불일치 시 실패 복귀.
- [x] `startAt`까지 남은 시간을 계산하는 timer 구현.
- [x] 남은 시간이 `countdownDisplaySeconds` 이하가 되면 `3`, `2`, `1` 표시.
- [x] 메시지를 늦게 받아 남은 시간이 2초대이면 `2`부터 표시 가능하도록 구현.
- [x] countdown timer는 unmount, 실패 복귀 시 정리.
- [x] countdown timer는 `GAME_START` 처리 시 정리.
- [x] Game Waiting loading bar는 payload 수신율 의미로 유지하고 countdown 진행률과 섞지 않음.

### 4. GameWaitingPage GAME_START 처리 구현

- [x] `GAME_START` 수신 시 route param `gameRoomId`와 payload `gameRoomId` 일치 검증.
- [x] `COUNTDOWN`을 먼저 받은 경우 `COUNTDOWN.startAt`과 `GAME_START.startAt` 일치 검증.
- [x] `COUNTDOWN` 없이 `GAME_START`가 먼저 오면 `GAME_START` payload를 source of truth로 저장.
- [x] `GAME_START` payload를 `sessionStorage`에 저장.
- [x] `GAME_START`를 final transition으로 표시해 늦은 close/error callback 무시.
- [x] `/game/:gameRoomId/play` 이동 구현.
- [x] play route 이동 실패 시 waiting 화면에 실패 상태를 유지하고 error message 표시.

### 5. GamePlayPage 최소 연결 구현

- [x] `/game/:gameRoomId/play`에서 저장된 game start payload 조회.
- [x] payload 없음, parse 실패, route param 불일치 시 `/match` 복귀.
- [x] 유효 payload가 있으면 `gameRoomId`, `startAt`, `starCoreMaxHp`, `durationMs`를 내부 상태로 보관.
- [x] 화면에는 이번 이슈 범위가 시작 데이터 수신/대기임을 나타내는 최소 상태만 표시.
- [x] `startAt` 기준 runtime 계산은 후속 play UI가 사용할 수 있도록 `getHpAtElapsedMs` helper와 연결 가능한 구조로 유지.

### 6. Locale 구현

- [x] Game Waiting countdown 상태 문구 한/영 추가.
- [x] Game Waiting game start 저장/이동 상태 문구 한/영 추가.
- [x] Game Waiting start payload 오류 문구 한/영 추가.
- [x] Game Play start payload missing 문구 한/영 추가.
- [x] Locale toggle 시 countdown/start 상태 문구가 전환되는지 확인.

### 7. Test 구현

- [x] game start payload 저장/조회 단위 테스트.
- [x] malformed payload, route param 불일치, storage missing 테스트.
- [x] `COUNTDOWN` 수신 시 countdown 상태와 문구 표시 테스트.
- [x] countdown 남은 시간이 늦게 시작되는 경우 `2` 또는 `1`부터 표시 가능한지 테스트.
- [x] `GAME_START` 수신 시 payload 저장 테스트.
- [x] `GAME_START` 수신 시 `/game/:gameRoomId/play` 이동 테스트.
- [x] `COUNTDOWN.startAt`과 `GAME_START.startAt` 불일치 시 play 이동 금지 테스트.
- [x] `GAME_START.payload.gameRoomId`와 route param 불일치 시 play 이동 금지 테스트.
- [x] `GAME_START` 이후 늦은 WebSocket close/error callback 무시 테스트.
- [x] `COUNTDOWN.payload.gameRoomId`와 route param 불일치 시 실패 복귀 테스트.
- [x] unmount 시 countdown timer 정리 테스트.
- [x] GamePlayPage payload missing 또는 mismatch 시 `/match` 복귀 테스트.
- [x] GamePlayPage 유효 payload 표시 테스트.
- [x] locale toggle 시 countdown/start 문구 전환 테스트.

### 8. 문서 정합성 구현

- [x] `front-plan.md` 9번 `게임 시작 처리 구현` 범위와 issue-88 범위 정합성 확인.
- [x] 구현 완료 후 `front-plan.md` 9번 `게임 시작 처리 구현`을 `[x]`로 체크.
- [x] 구현 완료 후 `front-plan.md`의 `## Issue Split Recommendation`에서 `게임 시작 처리 구현`을 `[x]`로 체크.
- [x] `front-plan.md` 10번 `게임 플레이 화면 구현`은 후속으로 유지.
- [x] `front-plan.md` 11번 `게임 결과 WebSocket 처리 구현`은 후속으로 유지.
- [x] `front-plan.md` 12번 `게임 결과 Summary 화면 구현`은 후속으로 유지.
- [x] issue-86의 `COUNTDOWN`, `GAME_START` 후속 이슈 문구와 issue-88 연결 확인.
- [x] `docs/project/websocket client.md`의 `COUNTDOWN`, `GAME_START` 클라이언트 처리 정책과 정합성 확인.
- [x] `docs/project/policy.md`의 GAME_START 시작 동기화 정책과 정합성 확인.
- [x] backend issue-46의 `startAt`, countdown, scenario 전달 정책과 정합성 확인.
- [x] 이번 이슈 PR 메시지 섹션 작성.

### 9. 검증

- [x] `npm run test -- GameWaitingPage gameStart` 검증.
- [x] `npm run format` 검증.
- [x] `npm run lint` 검증.
- [x] `npm run typecheck` 검증.
- [x] `npm run test` 검증.
- [x] `npm run build` 검증.
- [x] desktop viewport에서 countdown/start UI overflow 확인.
- [x] mobile viewport에서 countdown/start UI overflow 확인.
- [x] `npm run test -- GamePlayPage gameStartPayload hpScenario` 검증.

## Implementation Policy

- Game start 처리는 Game WebSocket 이벤트 기준으로 수행한다.
- `COUNTDOWN`은 시작 예고이며 scenario source가 아니다.
- `GAME_START`가 `serverTime`, `startAt`, `scenario`의 source of truth다.
- `GAME_START` 수신 후 `/game/:gameRoomId/play`로 이동한다.
- 실제 게임 시작 기준은 route 이동 시각이 아니라 `GAME_START.payload.startAt`이다.
- `COUNTDOWN`과 `GAME_START`가 모두 있으면 두 이벤트는 같은 `startAt`을 사용해야 한다.
- `COUNTDOWN` 없이 `GAME_START`가 먼저 와도 `GAME_START` payload가 유효하면 play로 이동한다.
- route param `gameRoomId`와 payload `gameRoomId`가 다르면 시작하지 않는다.
- server/client clock 보정은 이번 이슈에서 구현하지 않는다.
- `GAME_START` 이전 실패는 유효한 판이 아니므로 큐 자동 복귀, LP, 전적 흐름과 섞지 않는다.
- 실패 복귀 시 `joinMatchQueue`, `leaveMatchQueue`를 호출하지 않는다.
- Game Waiting loading bar는 payload 수신율이며 countdown 진행률이나 게임 시작 준비율이 아니다.
- 상태 관리는 page local state와 `sessionStorage` helper로 유지하고 Pinia를 도입하지 않는다.
- 새 패키지를 추가하지 않는다.

## Acceptance Criteria

- `COUNTDOWN` 수신 시 `/game/:gameRoomId/waiting`에 countdown 상태가 표시됨.
- countdown은 `startAt`까지 남은 시간 기준으로 표시됨.
- `GAME_START` 수신 시 `serverTime`, `startAt`, `scenario`가 저장됨.
- `GAME_START` 수신 후 `/game/:gameRoomId/play`로 이동함.
- play route는 저장된 game start payload를 읽을 수 있음.
- payload 없음 또는 route param 불일치 시 play route에서 `/match`로 복귀함.
- `COUNTDOWN.startAt`과 `GAME_START.startAt` 불일치 시 play route 이동이 발생하지 않음.
- `GAME_START` 이후 늦은 WebSocket close/error callback이 waiting 상태를 실패로 되돌리지 않음.
- Game Waiting loading bar 의미가 payload 수신율로 유지됨.
- lint / format / typecheck / test / build 통과.
- desktop/mobile viewport에서 horizontal overflow와 text overflow 후보가 없음.

## PR Message

## 📌 Summary

`/game/:gameRoomId/waiting` 화면에서 Game WebSocket `COUNTDOWN`, `GAME_START`를 처리해 게임 시작 payload를 저장하고 `/game/:gameRoomId/play`로 전환함.

이번 PR의 핵심은 Game Waiting 화면을 **게임 시작 직전 이벤트 수신 구간**까지 확장하되, 실제 게임 플레이 UI 책임은 후속 이슈로 넘기는 것임. `COUNTDOWN`은 시작 예고이고, `GAME_START`가 `serverTime`, `startAt`, `scenario`의 source of truth임. route 이동이 일어나도 실제 게임 시작 기준은 이동 시각이 아니라 백엔드가 확정한 `GAME_START.payload.startAt`으로 유지함.

```mermaid
flowchart TD
    A["Game Waiting WebSocket<br/>READY + RTT 완료"] --> B["COUNTDOWN 수신"]
    B --> C["startAt 기준 countdown 표시"]
    C --> D["GAME_START 수신"]
    D --> E{"gameRoomId / startAt / scenario 유효?"}
    E -->|no| F["WebSocket/watchdog/timer 정리"]
    F --> G["/match 복귀"]
    E -->|yes| H["GAME_START payload sessionStorage 저장"]
    H --> I["전환 완료 guard 설정"]
    J --> K["/game/:gameRoomId/play 이동"]
    K --> L["Play 최소 연결<br/>저장 payload 조회"]
    L --> M{"payload 유효?"}
    M -->|no| N["/match 복귀"]
    M -->|yes| O["startAt/scenario 확인<br/>후속 HUD 대기"]
```

핵심 정책:

- `COUNTDOWN`은 시작 예고 이벤트임.
- `GAME_START`가 시작 데이터와 scenario의 source of truth임.
- `GAME_START` 수신 후 play route로 이동하지만 실제 시작 기준은 `startAt`임.
- `COUNTDOWN`과 `GAME_START`가 모두 있으면 같은 `startAt`을 사용해야 함.
- `COUNTDOWN` 없이 `GAME_START`가 먼저 와도 payload가 유효하면 저장 후 play로 이동함.
- `GAME_START` 이후 늦은 WebSocket close/error callback은 실패 복귀로 덮어쓰지 않음.
- server/client clock 보정은 이번 PR에서 하지 않음.
- Game Waiting loading bar는 payload 수신율 의미로 유지함.

백엔드와의 구현 계약:

- `COUNTDOWN` payload는 `gameRoomId`, `serverTime`, `startAt`, `countdownDisplaySeconds`를 사용함.
- `GAME_START` payload는 `gameRoomId`, `serverTime`, `startAt`, `scenario`를 사용함.
- `scenario`는 League of Star 기준 `starCoreMaxHp`, `durationMs`, `hpTimeline`으로 구성될 수 있고, 백엔드 레거시 payload 호환을 위해 `starCoreMaxHp`도 허용함.
- 프론트는 백엔드가 내려준 `serverTime`, `startAt`을 그대로 저장함.
- route param `gameRoomId`와 payload `gameRoomId`가 다르면 잘못된 메시지로 보고 시작하지 않음.
- `COUNTDOWN.startAt`과 `GAME_START.startAt`이 다르면 백엔드 동기화 계약 위반으로 보고 시작하지 않음.
- `/game/:gameRoomId/play`는 `league-of-star.gameStartPayload:{gameRoomId}`만 읽음. payload가 없거나 route와 맞지 않으면 `/match`로 복귀함.
- `GAME_START` 이전 실패는 유효한 판이 아니므로 record/LP/큐 자동 복귀 흐름을 만들지 않음.

## 📚 Changes

- `COUNTDOWN`을 시작 예고로 처리함.
  서버는 `startAt` 전에 `COUNTDOWN`을 미리 보내며, 프론트는 남은 시간이 표시 구간에 들어왔을 때 countdown을 렌더링함. 메시지를 늦게 받으면 `3`이 아니라 `2` 또는 `1`부터 보일 수 있지만, 실제 시작 기준은 계속 `startAt`임.

- `GAME_START`를 시작 데이터의 source of truth로 저장함.
  scenario를 route state가 아니라 `sessionStorage`에 저장해 route 이동 이후에도 play 화면이 같은 시작 데이터를 읽게 함. 기존 game waiting payload 저장 정책과 같은 방식이므로 새 전역 store나 패키지를 추가하지 않음.

- WebSocket payload를 저장 전 검증함.
  `GAME_START` 메시지는 백엔드 이벤트지만 프론트는 런타임 payload shape를 그대로 신뢰하지 않음. `gameRoomId`, `serverTime`, `startAt`, `scenario`, `hpTimeline` 구조를 검증한 뒤 저장해 malformed payload가 play route까지 전파되지 않게 함.

- waiting과 play의 책임을 분리함.

- `startAt` 검증을 엄격하게 유지함.
  `COUNTDOWN`과 `GAME_START`가 서로 다른 `startAt`을 가지면 두 클라이언트의 시작 기준이 어긋날 수 있으므로 시작하지 않음. 이는 백엔드가 두 메시지에 같은 `startAt`을 사용한다는 계약을 프론트에서도 방어하는 처리임.

- `GAME_START` 이후 전환을 final 상태로 다룸.
  저장과 route 이동이 시작된 뒤 WebSocket close/error가 늦게 들어올 수 있으므로, 전환 완료 guard를 두어 waiting 화면이 다시 실패 복귀로 흔들리지 않게 함. 이는 성공 전환 기준을 WebSocket close가 아니라 `GAME_START` payload 저장과 play route 이동으로 분리하기 위한 처리임.

- 실패 복귀 정책을 issue-86과 동일하게 유지함.
  `GAME_START` 이전 실패는 아직 유효한 판이 아니므로 LP, 전적, 큐 자동 복귀와 섞지 않고 `/match`로 복귀함. 실패 복귀 시 match join/leave API도 호출하지 않음.

- loading bar 의미를 유지함.
  Game Waiting loading bar는 match/game/video/socket payload 수신율만 나타냄. countdown 진행률, WebSocket readiness, 실제 게임 시작 준비율과 섞지 않아 기존 UI 의미가 바뀌지 않게 함.

- 문서 정합성을 같이 관리함.
  `front-plan.md`의 9번 구현 단계와 `Issue Split Recommendation` 체크 상태를 `[x]`로 갱신하고, 10번 이후 play/result/summary 범위는 후속으로 유지함. issue-86에서 후속으로 남긴 `COUNTDOWN`, `GAME_START`, scenario 저장, play route 이동 범위를 issue-88에서 닫음.

## 📝 Note

- `LIGHTNING` 전송과 `GAME_RESULT` 수신 후 result route 이동은 후속 이슈에서 구현함.
- Game summary API 호출은 후속 결과 화면 이슈에서 구현함.
- clock skew 보정과 WebSocket 재접속/복구는 이번 범위가 아님.
- 실패 복귀 시 match join/leave API를 호출하지 않고 큐 자동 복귀도 하지 않음.
- 새 패키지는 추가하지 않음.
- 검증 완료: `format`, `lint`, `typecheck`, 전체 test, production build 통과함.
- 브라우저 검증 완료: desktop `1440x900`, mobile `390x844`에서 play 최소 연결 화면의 horizontal overflow 없음.
- 관련 테스트: `GameWaitingPage`, `GamePlayPage`, `gameStartPayload`, `hpScenario` 대상 테스트 통과함.
- 테스트 결과: 전체 테스트 14 files / 127 tests 통과함.

## 📌 Related Issue

- Closes #88
