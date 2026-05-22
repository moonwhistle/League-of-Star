# League of Smite — Policy

---

## 1. 매칭 정책

### 1.1 매칭 큐

| 규칙 | 내용 |
|------|------|
| **큐 진입 조건** | 로그인 상태 + 진행 중인 게임 없음 |
| **큐 취소** | 매칭 성사 전까지 자유롭게 취소 가능 |
| **중복 큐** | 불가 — 이미 큐에 있는 상태에서 재진입 차단 |

### 1.2 매칭 범위

| 대기 시간 | 매칭 범위 |
|-----------|----------|
| 0 ~ 10초 | ± 1 디비전 |
| 10 ~ 20초 | ± 2 디비전 |
| 20 ~ 30초 | ± 4 디비전 |
| 30초 ~ | ± 8 디비전 (최대 확장) |

- 매칭 범위는 **대기 시간에 비례하여 점진적으로 확장**
- Apex 티어(Master+)는 **LP 근접도** 기반 매칭 (± 100 LP 이내 → 점진 확장)
- 배치 게임 중인 유저(`RankSeries.type=PLACEMENT`)는 **Silver IV ~ Gold IV 구간** 유저와 매칭

### 1.3 매칭 수락

| 규칙 | 내용 |
|------|------|
| **수락 제한 시간** | 10초 |
| **양쪽 수락** | 게임방/시나리오 생성 성공 후 게임 대기 화면 진입 |
| **한 쪽 거절 / 타임아웃** | 10초 응답 윈도우 안에 수락한 유저는 큐 **최우선 복귀**, 거절/타임아웃/미응답 유저는 **큐 이탈 (패널티 없음)** |
| **게임방 생성 실패** | 양쪽 모두 start 버튼 화면으로 복귀 (자동 큐 복귀 없음, 패널티 없음) |
| **게임방 생성 후 Redis 상태 전환 실패** | 생성된 게임방/참여자를 `ABORTED`로 보상 처리하고 양쪽 모두 start 버튼 화면으로 복귀 |

- 매칭 거절에 대한 별도 패널티 없음 (자유롭게 거절 가능)
- 한 유저가 먼저 거절해도 상대방의 수락/거절 모달은 10초 제한 시간이 끝날 때까지 유지한다.
- 거절한 유저도 해당 matchId의 최종 정산이 끝날 때까지 매칭 응답 모달을 유지한다.
  - 거절 이후에는 수락/거절 버튼을 비활성화하고 정산 대기 상태로 표시한다.
  - 최종 `match_response_result` SSE 이벤트를 받은 뒤 start 버튼 화면으로 전환한다.
- 최종 실패 정산은 수락 제한 시간이 만료된 deadline 시점에 수행한다. 단, 양쪽 모두 수락한 경우는 즉시 성공 처리한다.
- 예: B가 3초에 거절하고 A가 6초에 수락하면, A는 제한 시간 안에 수락했으므로 기존 큐 진입 시각으로 최우선 복귀한다.
- 매칭 알림 SSE 연결은 `match_found` 수신 직후 닫지 않고, 매칭 응답 최종 결과를 받을 때까지 유지한다.
  - `match_response_result`는 해당 matchId의 매칭 SSE 최종 이벤트다.
  - 클라이언트는 `match_response_result` 수신 후 매칭 SSE `EventSource.close()`를 호출한다.
  - `GO_TO_GAME_WAITING`이면 매칭 SSE를 닫은 뒤 gameRoom WebSocket으로 전환한다.
- 양쪽 수락이 완료되어도 게임방/시나리오 생성과 Redis 상태 전환이 모두 성공하기 전에는 게임 대기 화면으로 이동시키지 않는다.
  - 게임방/시나리오 생성 성공 후 Redis `match session=ACCEPTED`, 두 유저 `match:status=IN_GAME` 전환까지 완료되면 `match_response_result`는 `GO_TO_GAME_WAITING`과 함께 `gameRoomId`, `videoUrl`, `webSocketUrl`을 전달한다.
  - 게임방/시나리오 생성 실패 또는 Redis 상태 전환 실패 시 `match_response_result`는 `FAILED / GAME_SETUP_FAILED / GO_TO_MATCH_START`를 전달한다.
  - Redis 상태 전환 실패가 게임방 생성 이후 발생하면 생성된 `game_rooms`와 `game_participants`는 `ABORTED`로 보상 처리한다.
  - 클라이언트는 `GAME_SETUP_FAILED` reason에 대응하는 안내 문구를 표시한 뒤 start 버튼 화면으로 복귀한다.
  - 이 경우 두 유저는 매칭 큐에 자동 복귀하지 않는다.
- accept/reject HTTP 응답은 사용자의 버튼 입력이 서버에 반영되었는지 알려주는 명령 응답이고, 매칭 성공/실패로 확정되는 최종 결과는 SSE 이벤트로 전달한다.
  - SSE는 상대방의 개별 응답 로그를 전달하지 않는다.
  - 클라이언트는 최종 결과의 `reason`과 `action`만 보고 화면을 전환한다.
  - 같은 `matchId`에서 HTTP 응답과 SSE 최종 이벤트의 도착 순서는 보장하지 않는다.
  - `match_response_result` SSE 이벤트는 해당 `matchId`의 최종 이벤트이므로, 클라이언트는 이후 도착하는 HTTP 응답의 화면 전환 값보다 SSE 이벤트를 우선한다.
- 매칭 SSE는 매칭 결과와 게임 대기 화면 진입 정보까지만 담당한다.
  - `GO_TO_GAME_WAITING` 이후 게임 준비, RTT 측정, 카운트다운, 게임 시작, SMITE 입력, 게임 종료는 WebSocket으로 처리한다.
  - 별도의 SSE `game_ready` 이벤트는 만들지 않는다.

#### 매칭 정산 표

| 응답 상태 | 정산 결과 |
|------|------|
| `ACCEPTED + ACCEPTED` | 매칭 성공 처리. timeout 대상 아님 |
| `ACCEPTED + REJECTED` | deadline 시 `DECLINED`, `ACCEPTED` 유저는 기존 큐 진입 시각으로 복귀 |
| `ACCEPTED + PENDING` | `PENDING` 유저는 `TIMEOUT`, `ACCEPTED` 유저는 기존 큐 진입 시각으로 복귀 |
| `REJECTED + REJECTED` | deadline 시 `DECLINED`, 두 유저 모두 큐 이탈 |
| `REJECTED + PENDING` | `PENDING` 유저는 `TIMEOUT`, 두 유저 모두 큐 이탈 |
| `PENDING + PENDING` | 두 유저 모두 `TIMEOUT`, 두 유저 모두 큐 이탈 |
| `ACCEPTED + ACCEPTED` 이후 gameRoom 생성 실패 | `GAME_SETUP_FAILED`, 두 유저 모두 start 버튼 화면 복귀. 큐 자동 복귀 없음 |
| `ACCEPTED + ACCEPTED` 이후 Redis 상태 전환 실패 | 생성된 gameRoom/participant `ABORTED`, `GAME_SETUP_FAILED`, 두 유저 모두 start 버튼 화면 복귀. 큐 자동 복귀 없음 |

---

## 2. 게임 진행 정책

### 2.1 게임 기본 규칙

| 규칙 | 내용 |
|------|------|
| **몬스터** | 장로 드래곤 (Elder Dragon) |
| **드래곤 초기 HP** | 10,000 |
| **강타 데미지** | 1,200 (True Damage, 고정) |
| **게임 제한 시간** | **8 ~ 17초** (매판 랜덤, 서버가 시나리오 생성 시 결정) |
| **강타 입력** | 1인당 **1회만** 가능 |
| **강타 사용 조건** | 드래곤 위에 마우스를 올린 상태에서 **D 또는 F 키** 입력 |
| **HP 감소 패턴** | 200ms 단위 랜덤 버스트 (서버 사전 생성 시나리오) |
| **몬스터 사망 시** | HP가 0에 도달하면 **즉시 게임 종료** |

### 2.1.1 HP 시나리오 생성 규칙

- 서버는 gameRoom 생성 시 `8 ~ 17초` 범위에서 duration을 먼저 정한다.
- HP timeline은 `200ms` 단위 step으로 생성한다.
- 첫 step은 `timeMs=0`, `hp=10000`이다.
- 마지막 step은 `timeMs=durationSeconds * 1000`, `hp=0`이다.
- HP는 `0 ~ 10000` 범위에 머물고, 이전 step보다 증가할 수 없다.
- 각 시나리오는 짧은 정체 구간과 큰 burst 구간을 포함하도록 보정한다.
- WebSocket/API 계층은 시나리오를 생성하지 않고, core의 `GameScenarioGenerator`가 생성 책임을 가진다.

### 2.2 강타 입력 규칙

- 드래곤 영역에 마우스 커서를 올린 상태에서 **D 또는 F 키를 누를 때** 강타 발동
- 마우스가 드래곤 영역 밖에 있으면 키를 눌러도 **강타가 발동되지 않음**
- 한 번 강타를 사용하면 **재사용 불가** (UI에서 비활성화)
- 게임 종료까지 강타를 사용하지 않으면 → 자동으로 **미사용 처리**

### 2.3 승패 판정

| 상황 | 결과 |
|------|------|
| 한 명만 킬 성공 (HP ≤ 1200 시점에 강타) | 해당 플레이어 **승리** |
| 둘 다 킬 성공 | **먼저 누른 사람** 승리 (선착순) |
| 둘 다 킬 실패 (드래곤 자연사) | **무승부** |
| 한 명만 강타 사용 + 킬 실패 | **무승부** (자연사) |
| 둘 다 강타 미사용 | **무승부** (자연사) |

### 2.4 게임 대기 timeout 및 디스커넥트 처리

| 시점 | 처리 |
|------|------|
| **GO_TO_GAME_WAITING 후 ~ CLIENT_READY 전** | gameRoom `createdAt`부터 **30초 안에 두 참가자가 WebSocket 연결과 `CLIENT_READY` 전송을 완료하지 못하면** gameRoom `ABORTED`. `game_records` 생성 없음, LP/배치/승급전 반영 없음 |
| **CLIENT_READY 완료 후 ~ GAME_START 전 RTT 측정** | 각 유저별 RTT 5회 측정. median RTT 2000ms 초과, `RTT_PONG` 응답 누락, WebSocket close/error, 측정 중 예외는 gameRoom `ABORTED`. `game_records` 생성 없음, LP/배치/승급전 반영 없음 |
| **GAME_START 이후 이탈** | disconnect 자체로 gameRoom을 `ABORTED` 처리하지 않음. 서버는 기존 gameStartTime, HP scenario, 수신된 SMITE 액션 기준으로 판을 끝까지 판정 |
| **GAME_START 이후 상대만 이탈** | 상대가 이탈해도 내 자동 승리가 아님. 내가 유효한 SMITE로 처치하면 승리, 처치하지 못하고 자연사하면 무승부 |
| **GAME_START 이후 양쪽 이탈** | 이미 수신된 액션이 없으면 자연사 기준 무승부. 이미 수신된 유효 액션이 있으면 해당 액션 기준으로 판정 |

- `GAME_START` 이전 timeout은 아직 유효한 판이 시작되지 않은 실패이므로 두 플레이어 모두 점수 변동이 없다.
- waiting timeout은 **30초**이며, 클라이언트 수신 시각이나 SSE 수신 시각이 아니라 DB에 저장된 gameRoom `createdAt`을 기준으로 계산한다.
- 30초 안에 두 참가자가 모두 WebSocket에 연결되고 `CLIENT_READY`까지 보내야 다음 RTT 측정 단계로 넘어갈 수 있다.
- RTT 측정은 `CLIENT_READY` 이후, `GAME_START` 이전 단계다. 따라서 RTT 실패/초과는 아직 유효한 판이 시작되지 않은 실패로 보고 `ABORTED` 처리한다.
- RTT 실패/초과 후 두 유저는 start 버튼 화면으로 복귀한다. 큐 자동 복귀는 하지 않는다.
- WebSocket에 연결되어 있던 유저에게만 `GAME_START_FAILED` 이벤트를 전송한 뒤 연결을 닫는다.
- WebSocket 미연결 유저는 API local registry에 session이 없으므로 WebSocket 상태를 저장하지 않는다. gameRoom과 participant는 timeout 전까지 DB상 `READY`를 유지한다.
- WebSocket 미연결 유저에게는 실시간 WebSocket 이벤트를 보낼 수 없다. timeout 후 늦게 WebSocket handshake를 시도하면 gameRoom이 이미 `ABORTED`이므로 연결을 거부하고, 클라이언트는 start 버튼 화면으로 복귀한다.
- WebSocket에 연결되어 있던 유저에게만 `GAME_WAITING_TIMEOUT` 이벤트를 전송한 뒤 연결을 닫는다.
- `GAME_START` 이전 timeout 후 두 유저는 start 버튼 화면으로 복귀한다. 큐 자동 복귀는 하지 않는다.
- `GAME_START` 이후 disconnect한 유저는 이후 추가 입력을 할 수 없지만, disconnect 전에 서버가 수신한 `SMITE` 액션은 그대로 유효하다.
- `GAME_START` 이후에는 WebSocket 연결이 모두 끊겨도 gameRoom 종료 작업은 서버 timer/scheduler 기준으로 완료한다.
- 서버 timer/scheduler는 `naturalDeathAt = startAt + scenario.durationMs`를 최초 자연사 deadline으로 등록한다.
- SMITE 실패 action이 저장되면 원본 scenario HP에서 누적 SMITE 데미지를 뺀 effective HP 기준으로 더 빠른 `naturalDeathAt`을 계산해 `game:end:pending` score를 앞당길 수 있다.
- `naturalDeathAt`은 정산 완료 시각이 아니라 scheduler가 정산 대상으로 조회할 수 있는 시작 시각이다. scheduler는 이 시각 이후 gameRoom을 다시 조회하고, 이미 `IN_PROGRESS`가 아니면 no-op 처리한다.
- 클라이언트 MP4 재생 지연, 브라우저 pause, 렌더링 지연은 서버의 종료 기준을 바꾸지 않는다. 서버 종료 기준은 `startAt + scenario.durationMs`다.
- `GAME_START` 확정 후 `game:end:pending` 등록에 실패하면 서버가 종료 정산을 보장할 수 없으므로 gameRoom과 participants를 `ABORTED` 처리하고 `COUNTDOWN`/`GAME_START`를 전송하지 않는다. 이 경우 `game:end:pending`, match user status, RTT 상태, waiting 상태 cleanup을 시도하고 `GAME_START_FAILED` 전송 후 WebSocket을 닫으며, record와 LP/티어 변동은 반영하지 않는다.
- `GAME_START` 메시지 전송에 실패하면 이미 등록된 `game:end:pending` deadline을 제거하고 gameRoom과 participants를 `ABORTED` 처리한다. 이 경우 match user status, RTT 상태, waiting 상태를 정리하고 `GAME_START_FAILED` 전송 후 WebSocket을 닫는다.
- game end scheduler는 `naturalDeathAt`에 도달한 gameRoom만 정산 대상으로 삼고, 정산 시 gameRoom이 이미 `IN_PROGRESS`가 아니면 no-op 처리한다.
- SMITE로 승/패가 확정되거나 두 유저가 모두 SMITE를 소모해 `DRAW`가 확정된 경우에도 `game:end:pending` member cleanup은 필수로 하지 않는다. DB의 `game_rooms.status`가 최종 기준이며, 후속 game end scheduler는 이미 `FINISHED`인 gameRoom을 no-op 처리한다.
- 자연사 종료 정산은 DB gameRoom 결과 확정을 먼저 수행하고, 연결된 local WebSocket session이 있으면 `GAME_RESULT`를 보낸다. 연결이 없거나 전송에 실패해도 DB 결과 확정은 되돌리지 않는다.
- 자연사 종료 후 pending cleanup은 WebSocket 전송 성공의 의미가 아니라 DB 기준으로 정산 완료된 후보를 제거하는 의미다. cleanup을 생략하면 이미 종료된 gameRoom이 scheduler tick마다 반복 조회될 수 있다.
- `GAME_RESULT.reason`은 종료 사유에 따라 `SMITE_KILL`, `BOTH_SMITES_USED_DRAW`, `NATURAL_DEATH_DRAW`를 사용한다.
- record/LP/배치/승급전 반영은 gameRoom 결과 확정 및 `GAME_RESULT` 전송 흐름과 분리한다. 현재 서버 종료 보장 범위에서는 `game_rooms`/`game_participants` 결과 확정까지만 수행하고, `game_records` 생성과 LP 반영은 후속 Step 9에서 처리한다.

### 2.5 서버 권위 타임스탬프 (공정성 핵심)

이 게임은 **1~10ms 차이로 승패가 결정**될 수 있으므로, 클라이언트가 보낸 시간을 신뢰하지 않습니다.

#### 판정 방식: 서버 권위 (Server-Authoritative)

```
1. 클라이언트 → 서버: WebSocket으로 "SMITE" 액션만 전송 (시간 정보 없음)
2. 서버: 수신 시각을 직접 기록 (server_receive_time)
3. 서버: 판정 시점 계산
   → smite_time = server_receive_time - game_start_time
4. 서버: 시나리오에서 smite_time 시점의 HP를 역산
5. 서버: HP ≤ 1200이면 킬 성공
```

- 클라이언트는 **시간 정보를 전송하지 않음** → 시간 조작 원천 차단
- 서버가 직접 측정한 수신 시각만 사용 → 판정의 신뢰성 확보
- 서버 판정 시간은 로컬 타임존 시간이 아니라 UTC `Instant` 기반 epoch milliseconds로 기록한다.
- DB의 `game_start_time`을 SMITE 판정에 사용할 때도 UTC 기준으로 epoch milliseconds로 변환해 `server_receive_time - game_start_time`을 계산한다.
- RTT 보정은 SMITE 판정에 사용하지 않음
- RTT 측정은 `GAME_START` 전 연결 품질 검사와 비정상 네트워크 환경 차단에만 사용
- SMITE 판정은 실제 롤 강타 감각에 맞춰 서버가 받은 입력 순서를 기준으로 처리
- 드래곤 초기 HP는 `10000`, SMITE 데미지는 `1200` 고정값으로 둔다.
- `game_actions.dragon_hp_at_smite`는 scenario 원본 HP가 아니라, 이전 SMITE 데미지를 반영한 이번 SMITE 적용 전 현재 HP를 저장한다.
- `game_actions`는 유저당 1회 SMITE 입력 기록으로 유지하고, 승패 기록과 LP/배치/승급전 반영은 `game_records`에서 처리한다.
- 킬 실패한 SMITE도 이후 HP 판정에는 `1200` 데미지로 반영한다.
- `afterHp = max(0, dragonHpAtSmite - 1200)`은 응답 payload에서 계산하고 DB에는 저장하지 않는다.
- `smiteTimeMs`가 HP timeline step 사이에 있으면 인접한 두 step의 HP를 선형 보간해 base HP를 계산한다.
- `smiteTimeMs < 100`은 게임 시작 직후 비정상적으로 빠른 입력으로 보고 action을 저장하지 않는다.
- `smiteTimeMs`가 scenario 범위를 벗어나면 action을 저장하지 않는다.
  - `startAt` 이전 입력은 무효 입력으로 본다.
  - scenario 종료 이후 입력은 자연사 이후 입력이므로 후속 종료 정산 흐름에서 현재 gameRoom 결과를 기준으로 처리한다.

#### RTT 측정

| 규칙 | 내용 |
|------|------|
| **측정 시점** | 게임 대기 WebSocket 연결 후 게임 시작 직전 |
| **측정 횟수** | 5회 Ping-Pong |
| **사용 값** | 중간값 (Median) — 극단값 제거 |
| **RTT 상한** | median RTT 2000ms 초과 시 게임 진입 차단 (안정적 환경에서 재시도 유도) |
| **개별 응답 제한** | 각 `RTT_PING`은 2500ms 안에 `RTT_PONG`을 받아야 함 |
| **전체 측정 제한** | 5회 측정과 per-ping 2500ms timeout 기준 gameRoom RTT 측정은 최대 15초 안에 완료되어야 함 |
| **실패 기준** | `RTT_PONG` 응답 누락, WebSocket close/error, 측정 중 예외는 `RTT_FAILED` |
| **초과 기준** | 5회 측정은 완료했지만 median RTT가 2000ms를 초과하면 `RTT_TOO_HIGH` |
| **성공 상태 보존** | RTT `PASSED` 상태는 `GAME_START` 결정 전까지 유지하고, SMITE 판정에는 사용하지 않음 |

#### GAME_START 시작 동기화

| 규칙 | 내용 |
|------|------|
| **진입 조건** | 양쪽 RTT가 모두 `PASSED`인 gameRoom만 `GAME_START`로 진입 |
| **시작 기준** | 서버가 `startAt = serverNow + 4000ms`로 절대 시작 시각을 확정 |
| **카운트다운 표시** | 클라이언트는 `startAt`까지 남은 시간이 3000ms 이하가 되면 `3, 2, 1`을 렌더링 |
| **메시지 전송 시점** | 서버는 `COUNTDOWN`과 `GAME_START`를 countdown 종료 후가 아니라 `startAt` 전에 미리 전송 |
| **GAME_START 처리** | 클라이언트는 `GAME_START`를 받아도 즉시 시작하지 않고, payload의 `startAt`까지 대기 |
| **동일 기준** | `COUNTDOWN`과 `GAME_START`는 반드시 같은 `startAt`을 사용 |
| **MP4 preload** | MP4 preload 완료 여부는 `CLIENT_READY` 전제로 보고 `GAME_START` 단계에서 다시 검증하지 않음 |

`startAt`을 서버 기준으로 고정하는 이유는 클라이언트마다 WebSocket 메시지를 받는 시점이 다를 수 있기 때문이다. 메시지를 받은 뒤 각자 3초를 세면 실제 시작 시각이 달라질 수 있으므로, 서버가 하나의 절대 시작 시각을 정하고 클라이언트는 그 시각까지 남은 시간만 렌더링한다.

#### 동시 판정 처리 (Tie-Breaking)

SMITE 판정에는 RTT 보정을 적용하지 않는다. 같은 gameRoom에서 두 사용자의 SMITE가 거의 동시에 들어와도 서버가 기록한 수신 시각을 기준으로 처리한다.

| 규칙 | 내용 |
|------|------|
| **판정 기준** | `serverReceiveTimeMs`가 빠른 action부터 판정 |
| **동일 수신 시각** | `id ASC` 순서로 판정 |
| **HP 반영** | 앞선 SMITE가 킬 실패였더라도 이후 action의 현재 HP에서 `1200`을 차감 |
| **결과 확정** | SMITE 적용 후 HP가 `0` 이하가 되면 즉시 `FINISHED`, 두 유저가 모두 실패하면 즉시 `DRAW` |

- 서버는 처치 SMITE를 저장한 transaction에서 gameRoom 결과를 확정한다.
- 두 유저가 모두 SMITE를 사용했고 둘 다 처치하지 못했다면 두 번째 실패 SMITE를 저장한 transaction에서 gameRoom을 `DRAW`로 확정한다.
- 결과가 확정되지 않은 SMITE는 중간 응답을 전송하지 않는다.
- SMITE로 결과가 확정되었으면 양쪽 클라이언트에 `GAME_RESULT`를 broadcast한다.
- 이미 `FINISHED`인 gameRoom에 늦게 도착한 SMITE는 새 action으로 저장하지 않고 현재 session에 `GAME_RESULT`만 재응답한다.
- record/LP 반영은 `GAME_RESULT` 전송 흐름과 분리한다. `game_records` 생성, LP 반영, 배치/승급전 처리는 후속 Step 9에서 확정된 gameRoom 결과를 기준으로 수행한다.

### 2.6 조작 방지

| 규칙 | 내용 |
|------|------|
| **시나리오 전달 시점** | 게임 카운트다운 완료 후 시작 시점에만 전달 (사전 유출 차단) |
| **입력 검증** | 클라이언트는 "SMITE" 액션만 전송, 시간 정보 포함 시 요청 무효 처리 |
| **셀프 매칭 방지** | 동일 IP에서 양쪽 플레이어 접속 시 매칭 차단 |
| **입력 시점 판정** | 게임 시작 후 비정상적으로 빠른 입력 (`smiteTimeMs < 100`) 또는 scenario 범위 밖 입력은 무효 처리 |
| **요청 중복 차단** | 동일 게임에서 2회 이상 SMITE 요청 수신 시 첫 번째만 유효 |

### 2.7 WebSocket 라우팅 정책

- 게임 WebSocket 연결/READY 상태는 API 인스턴스 local memory registry에서 관리한다.
- 멀티 인스턴스 환경에서는 같은 `gameRoomId`의 두 참가자가 같은 API 인스턴스로 연결되어야 한다.
- WebSocket routing affinity 기준은 userId가 아니라 `gameRoomId`다.
- `/ws/game/{gameRoomId}` 경로의 `gameRoomId` 기반 sticky routing을 사용한다.
- 이 보장이 없으면 `areBothConnected`, `areBothReady`, gameRoom broadcast가 인스턴스별로 갈라져 정확히 동작하지 않는다.
- Redis registry/pub-sub 기반 fan-out은 sticky routing으로 해결하기 어려운 확장 요구가 생길 때 후속으로 검토한다.

### 2.8 WebSocket session 상태 정책

| 상태 | 의미 |
|------|------|
| `CONNECTED` | handshake 성공 후 API local memory registry에 WebSocket session 등록 완료 |
| `READY` | 클라이언트가 `CLIENT_READY`를 보내 게임 대기 준비 완료 |
| `DISCONNECTED` | WebSocket 연결 종료로 registry에서 제거 |
| `REPLACED` | 같은 userId 재연결로 기존 session을 닫고 새 session으로 교체 |

- WebSocket session 상태는 DB에 저장하지 않는다.
- WebSocket session `READY`는 DB `game_participants.status=READY`와 다른 일시적 대기 상태다.
- GAME_START 이전 WebSocket 미접속, READY timeout, 연결 종료에 따른 gameRoom `ABORTED` 처리는 gameRoom `createdAt` 기준 30초 timeout 정책에서 수행한다.

---

## 3. LP 정책

### 3.1 LP 계산 공식

```
gap = 상대_티어점수 - 내_티어점수

승리 LP = clamp(25 + gap × 3, 15, 35)
패배 LP = clamp(25 - gap × 3, 15, 35)
무승부   = 0 (LP 변동 없음)
```

### 3.2 티어 점수 기준표

| 티어 | IV | III | II | I |
|------|-----|------|-----|-----|
| Iron | 1 | 2 | 3 | 4 |
| Bronze | 5 | 6 | 7 | 8 |
| Silver | 9 | 10 | 11 | 12 |
| Gold | 13 | 14 | 15 | 16 |
| Platinum | 17 | 18 | 19 | 20 |
| Emerald | 21 | 22 | 23 | 24 |
| Diamond | 25 | 26 | 27 | 28 |

#### LP 산정 프로세스 (2단계)

랭크 게임 종료 후의 LP 변동은 다음의 2단계 과정을 거쳐 결정됩니다.

**1단계: 티어 점수화 (Tier Scoring)**
*   각 플레이어의 현재 랭크(Tier + Division)를 고유한 숫자로 변환합니다.
*   공식: `Tier Score = (Tier_Level - 1) * 4 + (4 - Division_Value) + 1`
*   결과값 범위: Iron IV(1점) ~ Diamond I(28점)

**2단계: LP 변동량 계산 (LP Calculation)**
*   양측의 티어 점수 차이(`gap`)를 구하고, 이를 기본값(25 LP)에 가감합니다.
*   공식: `gap = 상대_티어점수 - 내_티어점수`
*   최종 LP: `clamp(25 ± gap * 3, 15, 35)`

#### 티어 점수(Tier Score) 계산 공식

매칭 및 LP 계산의 기준이 되는 티어 점수는 다음 수식에 의해 자동으로 결정됩니다.

```
Tier Score = (Tier_Level - 1) * 4 + (4 - Division_Value) + 1
```

*   **Tier_Level**: Iron(1), Bronze(2), Silver(3), Gold(4), Platinum(5), Emerald(6), Diamond(7)
*   **Division_Value**: I(1), II(2), III(3), IV(4)
*   **특이사항**: Master 이상의 Apex 티어는 별도의 LP 기반 점수를 사용합니다.

**계산 예시:**
*   **Iron IV**: (1 - 1) * 4 + (4 - 4) + 1 = **1점**
*   **Silver I**: (3 - 1) * 4 + (4 - 1) + 1 = 8 + 3 + 1 = **12점**
*   **Diamond I**: (7 - 1) * 4 + (4 - 1) + 1 = 24 + 3 + 1 = **28점**

### 3.3 LP 범위

| 규칙 | 내용 |
|------|------|
| **최소 LP** | 0 (음수 불가) |
| **최대 LP (일반 티어)** | 100 — 99 또는 100 도달 시 승급전 진입 |
| **최대 LP (Apex 티어)** | 무제한 |
| **LP 초과분 이월** | 승리로 100 LP 초과 시, 초과분은 승급전 진입 LP에 반영되지 않음 |

### 3.4 Apex 티어 LP 처리

| 구간 | LP 계산 |
|------|---------|
| **Master** | 동일 공식 적용 (gap은 상대 Master LP와의 차이를 100 LP당 ±1로 환산) |
| **LP 200 도달** | Grandmaster 자동 승급 |
| **LP 500 도달** | Challenger 자동 승급 |
| **LP 200 미만 하락 (GM)** | Master로 자동 강등 |
| **LP 500 미만 하락 (Challenger)** | Grandmaster로 자동 강등 |

### 3.5 승급전 LP 처리

| 상황 | 처리 |
|------|------|
| **승급전 진입** | 현재 LP 동결 (시리즈 결과 전까지 LP 변동 없음) |
| **승급전 성공** | `RankSeries.status=SUCCESS` 시 다음 디비전 LP 0에서 시작 |
| **승급전 실패** | `RankSeries.status=FAILED` 시 현재 디비전 LP 75로 세팅 |
| **GAME_START 이전 timeout/abort** | 유효한 판이 아니므로 시리즈 카운트에 반영하지 않음 |
| **GAME_START 이후 disconnect** | disconnect 자체가 아니라 최종 승/패/무승부 결과를 시리즈 카운트에 반영 |

### 3.6 특수 상황 LP 처리

| 상황 | 처리 |
|------|------|
| **GAME_START 이전 timeout/abort** | 유효한 판이 아니므로 LP 변동 없음 |
| **GAME_START 이후 최종 판정 패배** | 일반 패배와 동일한 LP 차감 |
| **GAME_START 이후 최종 판정 무승부** | 무승부와 동일하게 LP 변동 없음 |

---

## 4. 티어 & 승급 정책

### 4.1 승급전 (Promotion Series)

| 규칙 | 내용 |
|------|------|
| **진입 조건** | LP 99 또는 100 도달 |
| **방식** | **3판 진행, 2승 필수 (RankSeries로 관리)** |
| **승급 성공** | `RankSeries.status=SUCCESS` 도달 시 다음 디비전 LP 0 시작 |
| **승급 실패** | `RankSeries.status=FAILED` 도달 시 현재 디비전 LP 75 시작 |
| **적용 범위** | Iron IV ~ Diamond I 전 구간 + Diamond I → Master |

#### 승급전 판정 상세

3판을 진행하며, **승리 2회를 달성해야 승급**합니다. 무승부는 판수에 포함되지만 승리로 인정되지 않습니다.

| 3판 결과 예시 | 승급 여부 | 이유 |
|--------------|----------|------|
| 승 / 승 / - | ✅ 승급 | 2승 달성 (3판째 불필요) |
| 승 / 패 / 승 | ✅ 승급 | 3판 중 2승 |
| 승 / 무 / 승 | ✅ 승급 | 3판 중 2승 |
| 패 / 패 / - | ❌ 실패 | 남은 1판으로 2승 불가 (3판째 불필요) |
| 무 / 무 / - | ❌ 실패 | 남은 1판으로 2승 불가 (3판째 불필요) |
| 승 / 패 / 패 | ❌ 실패 | 3판 중 1승 |
| 승 / 패 / 무 | ❌ 실패 | 3판 중 1승 |
| 승 / 무 / 패 | ❌ 실패 | 3판 중 1승 |
| 무 / 승 / 패 | ❌ 실패 | 3판 중 1승 |
| 패 / 무 / 승 | ❌ 실패 | 3판 중 1승 |
| 무 / 패 / - | ❌ 실패 | 남은 1판으로 2승 불가 (3판째 불필요) |
| 패 / 무 / - | ❌ 실패 | 남은 1판으로 2승 불가 (3판째 불필요) |

> **조기 종료**: `RankSeries.checkCompletion()` 로직에 의해 2승 확정 또는 2승 달성이 불가능한 시점에 시리즈 즉시 종료 (SUCCESS/FAILED 판정)

### 4.2 디비전 간 승급/강등

| 규칙 | 내용 |
|------|------|
| **디비전 승급** | 승급전 성공 시 (예: Gold III → Gold II) |
| **티어 승급** | 승급전 성공 시 (예: Silver I → Gold IV) |
| **디비전 강등** | LP 0에서 패배 시 이전 디비전 LP 75로 강등 |
| **티어 강등** | LP 0에서 패배 시 이전 티어 I 디비전 LP 75로 강등 (예: Gold IV → Silver I, LP 75) |

### 4.3 강등 보호

| 규칙 | 내용 |
|------|------|
| **승급 직후 보호** | 승급 성공 후 **3패**까지 강등 보호 (LP 0 이하로 내려가도 강등 없음) |
| **보호 소진** | 3패 소진 또는 다음 승리 시 보호 해제 |
| **Iron IV 바닥** | Iron IV LP 0에서 패배 시 LP 0 유지 (더 이상 강등 없음) |

### 4.4 Apex 티어 승급

| 구간 | 방식 |
|------|------|
| **Diamond I → Master** | 승급전 3판 2승 필수 (최종 관문) |
| **Master → Grandmaster** | LP 200 도달 시 **자동 승급** (승급전 없음) |
| **Grandmaster → Challenger** | LP 500 도달 시 **자동 승급** (승급전 없음) |

### 4.5 Apex 티어 강등

| 구간 | 조건 |
|------|------|
| **Challenger → GM** | LP 500 미만으로 하락 시 자동 강등 |
| **GM → Master** | LP 200 미만으로 하락 시 자동 강등 |
| **Master → Diamond I** | LP 0에서 패배 시 Diamond I LP 75로 강등 |

---

## 5. 배치 정책

### 5.1 배치 대상

| 규칙 | 내용 |
|------|------|
| **대상** | 최초 가입 후 랭크 게임을 처음 시작하는 유저 |
| **배치 판수** | 10판 (RankSeries 관리) |
| **배치 중 티어 표시** | "Unranked" (배치 완료 전까지 티어 미표시) |

### 5.2 배치 중 매칭

- 배치 유저는 **Silver IV ~ Gold IV 구간 (티어 점수 9~16)** 유저와 매칭
- 배치 유저끼리 매칭될 수도 있음
- 매칭 범위 확장 규칙은 일반 매칭과 동일 (대기 시간 비례 확장)

### 5.3 배치 결과 배정

| 배치 승수 | 배정 티어 | 배정 LP |
|-----------|----------|---------|
| 0 ~ 2승 | Iron IV | 0 |
| 3 ~ 4승 | Bronze IV | 0 |
| 5 ~ 6승 | Silver IV | 0 |
| 7 ~ 8승 | Gold IV | 0 |
| 9 ~ 10승 | Platinum IV | 0 |

- 무승부는 **승수에 포함되지 않음** (10판의 승/패 결과만 카운트)

### 5.4 배치 중 LP/승급

- 배치 기간 중에는 **LP 변동/승급전이 발생하지 않음**
- 10판의 승/패 결과가 확정되면 즉시 티어 배정
- 배치 중 `GAME_START` 이전 timeout/abort는 배치 판수에 반영하지 않는다.
- 배치 중 `GAME_START` 이후 disconnect는 최종 승/패/무승부 결과를 배치 판수에 반영한다.

---

## 6. 계정 정책

### 6.1 회원가입

| 규칙 | 내용 |
|------|------|
| **가입 방식** | 이메일 + 비밀번호 / 소셜 로그인 (Google, Discord) |
| **비밀번호 규칙** | 최소 8자, 영문 + 숫자 포함 |

### 6.2 닉네임

| 규칙 | 내용 |
|------|------|
| **길이** | 2 ~ 16자 |
| **허용 문자** | 한글, 영문, 숫자 |
| **금지** | 특수문자, 공백, 비속어 필터링 적용 |
| **중복** | 불가 (대소문자 구분 없이 유니크) |
| **변경** | 가능 (추후 쿨타임 정책 검토) |

### 6.3 소셜 로그인 연동

| 규칙 | 내용 |
|------|------|
| **지원 플랫폼** | Google, Discord |
| **계정 연동** | 이메일 기준으로 기존 계정과 자동 연동 |
| **연동 해제** | 비밀번호 설정 후 소셜 연동 해제 가능 |

### 6.4 계정 탈퇴

| 규칙 | 내용 |
|------|------|
| **탈퇴 처리** | 요청 후 7일 유예 기간 (유예 기간 내 취소 가능) |
| **데이터 처리** | 유예 기간 후 개인정보 삭제, 전적 기록은 익명화하여 보존 |
| **재가입** | 탈퇴 후 30일 이후 동일 이메일로 재가입 가능 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-04-17 | 초안 작성 (매칭, 게임 진행, LP, 티어 & 승급, 배치, 계정 정책) |
| 2026-04-17 | 매칭 거절 패널티 제거, 게임 시작 세팅 제거(D/F 둘 다 강타 발동으로 단순화), 게임 시간 랜덤, 마우스 호버 조건 추가, 서버 권위 타임스탬프 방식 전환, 승급전 3판 2승 필수(무승부 불인정) |
| 2026-04-27 | 통합 시리즈 아키텍처(RankSeries) 도입 및 배치/승급 정책 일원화 |
| 2026-05-13 | 양쪽 수락 후 gameRoom/scenario 생성 성공 시에만 `GO_TO_GAME_WAITING` 발행, gameRoom 생성 실패 시 `GAME_SETUP_FAILED` 실패 이벤트 발행, 매칭 SSE와 게임 WebSocket 책임 경계 반영 |
| 2026-05-13 | gameRoom 생성 실패 시 자동 큐 복귀하지 않고 `GO_TO_MATCH_START`와 `GAME_SETUP_FAILED` reason으로 start 화면 복귀하도록 정책 변경 |
| 2026-05-13 | 양쪽 수락 후 Redis 상태 전환까지 성공해야 `GO_TO_GAME_WAITING`을 발행하고, Redis 실패 시 gameRoom/participant `ABORTED` 보상 처리 정책 추가 |
| 2026-05-14 | `match_response_result` 수신 후 클라이언트가 매칭 SSE `EventSource.close()`를 호출하는 책임 명시 |
| 2026-05-15 | 게임 WebSocket local registry 사용 전제와 멀티 인스턴스 `gameRoomId` 기반 sticky routing 정책 추가 |
| 2026-05-15 | WebSocket session `CONNECTED`, `READY`, `DISCONNECTED`, `REPLACED` 상태 정책 추가 |
| 2026-05-18 | GAME_START 이전 timeout은 `ABORTED` 및 record/LP 미반영, GAME_START 이후 disconnect는 중단 없이 정상 판정 흐름으로 처리하도록 정책 조정 |
| 2026-05-18 | 게임 대기 WebSocket timeout을 gameRoom `createdAt` 기준 30초로 확정 |
| 2026-05-18 | 게임 대기 WebSocket 미연결 유저는 timeout 전까지 저장 상태 없음, timeout 후 이벤트 수신 불가 및 late handshake 거절 정책 명시 |
| 2026-05-19 | RTT 5회 median 측정, 2500ms per-ping timeout, 15초 전체 제한, RTT 실패/초과 시 GAME_START 이전 `ABORTED` 정책 추가 |
| 2026-05-20 | GAME_START 진입 조건, `startAt = serverNow + 4000ms`, 프론트 3초 countdown 렌더링, COUNTDOWN/GAME_START 사전 전송 정책 추가 |
