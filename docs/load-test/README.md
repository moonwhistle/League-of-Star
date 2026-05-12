# 매칭 부하 테스트

매칭 부하 테스트는 세 가지 축으로 나눕니다.

| 스크립트 | 목적 | 도구 |
| --- | --- | --- |
| `join-queue-steady.js` | 일정 TPS로 `joinQueue`를 지속 유입 | k6 |
| `join-queue-burst.js` | 정해진 인원을 짧은 시간에 순간 유입 | k6 |
| `sse-notification-load.mjs` | SSE 연결 유지와 `match_found` 수신 검증 | Node |
| `match-response-timeout-load.mjs` | 수락/거절/timeout 정책과 `match_response_*` 지표 검증 | Node |

## 실행 환경

infra, app, monitoring 순서로 실행합니다.

```bash
docker compose -f infra/local/docker-compose-infra.yml up -d
docker compose -f infra/local/docker-compose-app.yml up -d --build
docker compose -f infra/local/docker-compose-monitoring.yml up -d
```

API 주소는 공통으로 지정할 수 있습니다.

```bash
API_BASE_URLS=http://localhost:8080,http://localhost:8081
```

Redis 초기화 명령어

```bash
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "match:status:*"); do redis-cli del "$key"; done'
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "matching:queue:*"); do redis-cli del "$key"; done'
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "match:session:*"); do redis-cli del "$key"; done'
docker exec smite-redis redis-cli del match:response:timeout:pending
docker exec smite-redis redis-cli del match:response:timeout:processing
```

## Join Queue

매칭 엔진 자체 처리량과 큐 적체를 확인할 때 사용합니다.

기본 기준은 이벤트 피크인 10,000명입니다.

```bash
TARGET_TPS=50 DURATION=5m VUS=400 k6 run docs/load-test/join-queue-steady.js
```

순간 유입은 burst 스크립트를 사용합니다.

```bash
TOTAL_USERS=10000 VUS=1000 k6 run docs/load-test/join-queue-burst.js
```

확인 지표:

- `joinQueue` 5xx 비율 1% 미만
- `joinQueue` p95 300ms 미만
- 전체 대기 인원이 테스트 종료 후 1분 내 0에 수렴하는지
- 초당 매칭 성사 수가 유입 TPS의 절반에 근접하는지
- 스캔 소요 시간 p95/p99가 scheduler 주기를 침범하지 않는지

## SSE Notification

SSE 연결 유지, heartbeat, `match_found` 전송률을 확인합니다.

```bash
CONNECTIONS=10000 HOLD_DURATION=5m node docs/load-test/sse-notification-load.mjs
MODE=match CONNECTIONS=10000 JOIN_TPS=50 HOLD_DURATION=5m node docs/load-test/sse-notification-load.mjs
```

확인 지표:

- SSE 연결 성공률
- heartbeat 수신 수
- join 성공 대비 `match_found` 수신률
- `match_found` 수신 지연 p95/p99

## Match Response / Timeout

`match_found` 이벤트에서 받은 `matchId` 기준으로 pair를 묶고, 시나리오별로 accept/reject/timeout을 재현합니다.

기본 기준은 10,000명, `JOIN_TPS=50`입니다. 단일 시나리오는 필요할 때 `SCENARIO`만 바꿔 실행합니다.

```bash
SCENARIO=both_accept node docs/load-test/match-response-timeout-load.mjs
```

둘 다 수락해서 `ACCEPTED` 완료 처리량을 확인합니다. 큐 복귀가 없어 가장 순수한 응답 처리량 기준입니다.

```bash
SCENARIO=accept_reject node docs/load-test/match-response-timeout-load.mjs
```

한 명이 먼저 수락하고 상대가 거절합니다. 수락자 큐 복귀가 발생하므로 재매칭 이벤트는 무시하고 별도 카운트합니다.

```bash
SCENARIO=reject_accept node docs/load-test/match-response-timeout-load.mjs
```

한 명이 먼저 거절하고 상대가 10초 안에 수락합니다. 후행 수락자 큐 복귀 정책과 `DECLINED` 완료 처리를 확인합니다.

```bash
SCENARIO=one_accept_other_timeout TIMEOUT_WAIT=15s node docs/load-test/match-response-timeout-load.mjs
```

한 명만 수락하고 상대는 응답하지 않습니다. timeout 정산 후 수락자 큐 복귀 지표를 확인합니다.

```bash
SCENARIO=one_reject_other_silent TIMEOUT_WAIT=15s node docs/load-test/match-response-timeout-load.mjs
```

한 명은 거절하고 상대는 응답하지 않습니다. 거절 후 남은 pending 유저의 timeout 정산을 확인합니다.

```bash
SCENARIO=both_timeout TIMEOUT_WAIT=15s node docs/load-test/match-response-timeout-load.mjs
```

둘 다 응답하지 않습니다. timeout claim/settlement/backlog 지표를 확인하는 순수 timeout 시나리오입니다.

```bash
SCENARIO=mixed node docs/load-test/match-response-timeout-load.mjs
```

수락/거절/timeout을 기본 비율로 섞어 운영형 응답 부하를 만듭니다. 큐 복귀로 생긴 재매칭 이벤트는 무시하고 별도 집계합니다.

작게 검증할 때만 `USERS`와 `JOIN_TPS`를 낮춥니다.

```bash
SCENARIO=mixed USERS=200 JOIN_TPS=20 node docs/load-test/match-response-timeout-load.mjs
```

지원 시나리오:

| SCENARIO | 동작 | 주로 확인할 지표 |
| --- | --- | --- |
| `both_accept` | A/B 모두 수락 | accepted completion |
| `accept_reject` | A 수락 후 B 거절 | declined completion, 큐 복귀 정책 |
| `reject_accept` | A 거절 후 B 수락 | 10초 모달 내 후행 수락 정책 |
| `one_reject_other_silent` | A 거절, B 무응답 | timeout 정산과 no-op 여부 |
| `one_accept_other_timeout` | A 수락, B timeout | queue returned users |
| `both_timeout` | A/B 모두 무응답 | timeout claim/settlement |
| `mixed` | 여러 시나리오 비율 혼합 | 운영형 응답 부하 |

주요 옵션:

| 옵션 | 기본값 | 설명 |
| --- | --- | --- |
| `USERS` | `10000` | 테스트 유저 수. 짝수여야 합니다. |
| `JOIN_TPS` | `50` | joinQueue 유입 속도 |
| `SCENARIO` | `mixed` | 실행할 응답 정책 시나리오 |
| `RESPONSE_DELAY_MS` | `100` | 첫 응답과 두 번째 응답 사이 간격 |
| `TIMEOUT_WAIT` | `15s` | timeout scheduler 정산 대기 시간 |
| `HOLD_DURATION` | 자동 계산 | SSE 유지 시간. 기본은 `USERS / JOIN_TPS + TIMEOUT_WAIT + buffer`입니다. |
| `CLEANUP_REQUEUED` | `false` | 큐 복귀 유저를 leave 처리할지 여부. 기본은 재매칭 이벤트를 무시하고 별도 집계합니다. |
| `MIXED_WEIGHTS` | `both_accept:60,accept_reject:15,reject_accept:15,one_accept_other_timeout:5,both_timeout:5` | mixed 비율 |

확인 지표:

```promql
sum by (action, result) (rate(match_response_requests_total[1m]))
sum(rate(match_response_requests_total{result="failure"}[5m])) / clamp_min(sum(rate(match_response_requests_total{result="attempt"}[5m])), 0.001) * 100
sum by (result) (rate(match_response_completions_total[1m]))
sum by (action) (rate(match_response_lock_failures_total[1m]))
sum(match_response_timeout_pending_backlog)
sum(match_response_timeout_processing_backlog)
sum by (outcome) (rate(match_response_timeout_claims_total[1m]))
sum by (outcome) (rate(match_response_timeout_settlements_total[1m]))
sum(rate(match_response_timeout_queue_returned_users_total[1m]))
histogram_quantile(0.95, sum(rate(match_response_timeout_processing_delay_seconds_bucket[5m])) by (le))
```

Grafana에서는 다음 대시보드를 봅니다.

- 매칭 엔진: `docs/grafana/smite-match-queue-dashboard.json`
- 매칭 응답/timeout: `docs/grafana/smite-match-response-dashboard.json`
