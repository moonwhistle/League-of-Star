# 매칭 부하 테스트

매칭 부하 테스트는 세 가지 축으로 나눕니다.

| 스크립트 | 목적 | 도구 |
| --- | --- | --- |
| `join-queue-steady.js` | 일정 TPS로 `joinQueue`를 지속 유입 | k6 |
| `join-queue-burst.js` | 정해진 인원을 짧은 시간에 순간 유입 | k6 |
| `sse-notification-load.mjs` | SSE 연결 유지와 `match_found` 수신 검증 | Node |
| `match-response-timeout-load.mjs` | 수락/거절/timeout 정책과 `match_response_*` 지표 검증 | Node |
| `redis/matching-e2e-benchmark.sh` | HTTP 진입부터 세션 확정까지 전체 매칭 시간 측정 | k6 + Redis CLI |
| `redis/matching-lease-recovery-benchmark.sh` | worker 종료 후 Stream PEL 복구 검증 | Redis CLI + Docker |

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
docker exec league-of-star-redis sh -c 'for key in $(redis-cli --scan --pattern "match:status:*"); do redis-cli del "$key"; done'
docker exec league-of-star-redis sh -c 'for key in $(redis-cli --scan --pattern "matching:queue:*"); do redis-cli del "$key"; done'
docker exec league-of-star-redis redis-cli del matching:queue
docker exec league-of-star-redis redis-cli del matching:jobs
docker exec league-of-star-redis sh -c 'for key in $(redis-cli --scan --pattern "match:session:*"); do redis-cli del "$key"; done'
docker exec league-of-star-redis redis-cli del match:response:timeout:pending
docker exec league-of-star-redis redis-cli del match:response:timeout:processing
```

## Join Queue

매칭 엔진 자체 처리량과 큐 적체를 확인할 때 사용합니다.

기본 기준은 컨테이너 OOM을 피하면서 정책을 검증할 수 있는 5,000명입니다.

### Matching Engine Stream Job

API, 세션 저장, 알림 발행을 제외하고 Redis pairing producer만 비교합니다. 독립된 Redis connection을 사용하는 worker 2개가 동시에 FIFO 대기열을 Stream MatchJob으로 전환하며, 배치 크기별 소진 시간·Lua 호출 수·중복 여부를 측정합니다.

```bash
USERS=5000 WORKERS=2 WARMUPS=2 REPETITIONS=7 \
  BATCH_SIZES=20,50,100,200,500,1000 \
  OUTPUT_FILE=docs/load-test/result/matching/redis/matching-engine-benchmark.json \
  node docs/load-test/redis/matching-engine-benchmark.mjs
```

실행 전 Redis가 `127.0.0.1:6379`에서 실행 중이어야 합니다. 스크립트는 각 시나리오 시작 전 `FLUSHALL`을 실행하므로 로컬 테스트용 Redis에서만 사용합니다.

```bash
TARGET_TPS=50 DURATION=5m VUS=400 k6 run docs/load-test/join-queue-steady.js
```

순간 유입은 burst 스크립트를 사용합니다.

```bash
TOTAL_USERS=5000 VUS=500 k6 run docs/load-test/join-queue-burst.js
```

HTTP 진입부터 2,500개 매칭 세션 확정까지 전체 시간을 측정합니다. 종료 조건은 `waiting=0`, `Stream PEL=0`, `pending timeout=2,500`입니다.

```bash
TOTAL_USERS=5000 VUS=500 TIMEOUT_SECONDS=90 \
  bash docs/load-test/redis/matching-e2e-benchmark.sh improved-batch-ack
```

Stream PEL 복구는 실제 DB 사용자 ID를 FIFO에 준비한 뒤, 첫 번째 인스턴스가 MatchJob을 읽어 PEL에 등록한 순간 종료하고 두 번째 인스턴스의 `XAUTOCLAIM` 재처리 결과를 확인합니다. 로컬 전용 스크립트이며 실행 중 애플리케이션 컨테이너를 pause/kill/start합니다.

```bash
TOTAL_USERS=5000 TIMEOUT_SECONDS=90 \
  bash docs/load-test/redis/matching-lease-recovery-benchmark.sh lease-recovery
```

Redis 명령 수와 네트워크 전송량은 시나리오 실행 직전에 통계를 초기화하고, 큐가 모두 소진된 직후 스냅샷을 저장합니다.

```bash
TOTAL_USERS=5000 TEST_USER_NAMESPACE=redis-metrics \
  TOKENS_FILE=/tmp/league-of-star-load-test-tokens.json \
  node docs/load-test/prepare-join-users.mjs

bash docs/load-test/redis/measure-commandstats.sh fifo-5000 reset
TOTAL_USERS=5000 VUS=500 \
  TOKENS_FILE=/tmp/league-of-star-load-test-tokens.json \
  k6 run docs/load-test/join-queue-burst.js
bash docs/load-test/redis/measure-commandstats.sh fifo-5000 snapshot
```

토큰 준비를 분리해야 로그인·회원가입 과정의 Redis 명령이 매칭 측정값에 섞이지 않습니다. 토큰 파일은 기본적으로 `/tmp`에 생성하며 저장소에 커밋하지 않습니다. 결과는 `docs/load-test/result/matching/redis/<label>.txt`에 저장됩니다. `INFO commandstats`의 명령별 호출 수와 CPU 시간뿐 아니라 `total_commands_processed`, `total_net_input_bytes`, `total_net_output_bytes`를 함께 기록합니다. 테스트마다 Redis 통계를 초기화하므로 다른 시나리오와 동시에 실행하지 않습니다.

peak ops/s와 Redis CPU·메모리 추이는 별도 실행에서 샘플링합니다. 샘플러의 `INFO` 명령이 전체 명령 수와 네트워크 바이트에 포함되므로, 명령 수는 위의 비샘플링 실행 결과를 사용합니다.

```bash
INTERVAL_SECONDS=0.2 bash docs/load-test/redis/sample-runtime.sh fifo-5000
# 다른 터미널에서 burst 실행 후 종료
touch /tmp/league-of-star-redis-sampler.stop
```

확인 지표:

- `joinQueue` 5xx 비율 1% 미만
- `joinQueue` p95 300ms 미만
- 전체 대기 인원이 테스트 종료 후 1분 내 0에 수렴하는지
- 초당 매칭 성사 수가 유입 TPS의 절반에 근접하는지
- 스캔 소요 시간 p95/p99가 scheduler 주기를 침범하지 않는지
- 사용자 수 대비 전체 Redis 명령 수와 네트워크 입출력 바이트
- `EVAL`, `ZADD`, `SET`, `HSET`, `PEXPIRE`, `PUBLISH` 호출 수
- Lua 내부 명령을 제외한 애플리케이션-Redis 왕복 횟수

## SSE Notification

SSE 연결 유지, heartbeat, `match_found` 전송률을 확인합니다.

```bash
CONNECTIONS=5000 HOLD_DURATION=5m node docs/load-test/sse-notification-load.mjs
MODE=match CONNECTIONS=5000 JOIN_TPS=50 HOLD_DURATION=5m node docs/load-test/sse-notification-load.mjs
```

확인 지표:

- SSE 연결 성공률
- heartbeat 수신 수
- join 성공 대비 `match_found` 수신률
- `match_found` 수신 지연 p95/p99

## Match Response / Timeout

`match_found` 이벤트에서 받은 `matchId` 기준으로 pair를 묶고, 시나리오별로 accept/reject/timeout을 재현합니다.

기본 기준은 5,000명, `JOIN_TPS=50`입니다. 단일 시나리오는 필요할 때 `SCENARIO`만 바꿔 실행합니다.

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
| `USERS` | `5000` | 테스트 유저 수. 짝수여야 합니다. |
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

부하테스트 결과 총량은 `ops/s` 패널보다 매칭 응답 대시보드의 `Load Test Totals` 섹션에서 확인합니다.
해당 섹션은 구간별 `increase(...[$__rate_interval])` 그래프로 표시하며, legend의 `sum` 값으로 선택한 시간 범위의 총 처리량을 확인합니다.

주요 비교 기준:

```text
one_accept_other_timeout:
- accept success sum ~= handled pairs
- timeout settlement success sum ~= handled pairs
- returned users sum ~= handled pairs

one_reject_other_silent:
- reject success sum ~= handled pairs
- timeout settlement success sum ~= handled pairs
- returned users sum ~= 0
```

Grafana에서는 다음 대시보드를 봅니다.

- 매칭 엔진: `docs/grafana/league-of-star-match-queue-dashboard.json`
- 매칭 응답/timeout: `docs/grafana/league-of-star-match-response-dashboard.json`
  - `Load Test Totals`: 부하테스트 총량 추이와 legend 합계
  - `JVM / 애플리케이션 리소스`: CPU, Heap/Non-Heap, GC, Thread, SSE active connection
