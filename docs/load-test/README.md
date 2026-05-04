# 매칭 부하 테스트

이 문서는 `POST /api/v1/match/join`과 매칭 엔진 V1의 부하 테스트 기준을 정의합니다.

테스트의 목적은 단순히 많은 요청을 보내는 것이 아니라, **매칭 엔진 개선 전/후를 같은 기준으로 측정해서 개선 수치를 비교**하는 것입니다. 따라서 먼저 예상 사용자 규모와 목표 TPS를 고정하고, 이후 V1/V2/V3를 같은 시나리오로 반복 측정합니다.

---

## 1. 테스트 기준

### 1.1 서비스 가정

| 구분 | 동시 접속 사용자 | 매칭 진입 비율 | 매칭 진입 시간 | 목표 joinQueue TPS |
| --- | ---: | ---: | ---: | ---: |
| 일반 피크 | 1,000명 | 30% | 60초 | 5 TPS |
| 높은 피크 | 5,000명 | 30% | 60초 | 25 TPS |
| 이벤트 피크 | 10,000명 | 30% | 60초 | 50 TPS |

계산 방식:

```text
목표 TPS = 동시 접속 사용자 수 * 매칭 진입 비율 / 매칭 진입 시간
```

예:

```text
1,000명 * 30% / 60초 = 5 TPS
5,000명 * 30% / 60초 = 25 TPS
10,000명 * 30% / 60초 = 50 TPS
```

### 1.2 유지 시간

기본 유지 시간은 5분입니다.

포트폴리오 관점에서는 5분 기준으로 V1/V2/V3를 반복 비교하고, 최종 안정성 확인이 필요할 때 10분 테스트를 추가합니다.

| TARGET_TPS | DURATION | 총 joinQueue 요청 수 |
| ---: | ---: | ---: |
| 5 TPS | 5분 | 1,500명 |
| 25 TPS | 5분 | 7,500명 |
| 50 TPS | 5분 | 15,000명 |

### 1.3 성능 기준

이 게임은 짧은 캐주얼 1:1 대전이므로, 매칭 대기 시간은 일반적인 RPG/랭크 게임보다 훨씬 짧게 잡습니다.

`매칭 성공 유저 대기 시간`은 `joinQueue` 진입부터 `match found` 처리까지 걸린 시간입니다. 수락/거절 모달 유효 시간 10초와는 별도 정책입니다.

| 테스트 유형 | 목표 | 허용 | 개선 필요 |
| --- | ---: | ---: | ---: |
| steady 매칭 대기 p95 | 1초 미만 | 2초 미만 | 2초 이상 |
| burst 매칭 대기 p95 | 3초 미만 | 5초 미만 | 5초 이상 |

목표 기준은 포트폴리오와 최종 개선 목표로 사용합니다. 허용 기준은 V1이 장애 없이 처리 가능한 최소선이며, 허용 기준을 넘으면 매칭 엔진 구조 개선 대상으로 판단합니다.

### 1.4 공통 성공 기준

| 지표 | 성공 기준 | 이유 |
| --- | --- | --- |
| joinQueue 5xx 비율 | 1% 미만 | API 진입 경로 장애 여부 |
| joinQueue p95 응답 시간 | 300ms 미만 | 사용자가 매칭 버튼을 눌렀을 때의 체감 응답 |
| 전체 대기 인원 | 테스트 종료 후 1분 내 0 수렴 | 매칭 엔진이 유입을 소화했는지 확인 |
| 초당 매칭 성사 수 | 목표 TPS의 절반에 근접 | 유저 2명당 1페어가 성사되기 때문 |
| 스캔 소요 시간 p95 | 스케줄 주기보다 충분히 낮아야 함 | 전체 스캔 기반 V1 구조의 비용 확인 |
| 원자 제거 실패율 | join-only 테스트에서는 0에 가까워야 함 | 취소/중복/상태 불일치가 없는 시나리오이기 때문 |
| 락 스킵 수 | 큐 적체와 함께 증가하지 않아야 함 | 멀티 인스턴스 전역 락 경합 영향 확인 |

---

## 2. 우선 실행할 시나리오

### 2.1 일반 피크: 5 TPS / 5분

평소 동시 접속 사용자 1,000명 중 30%가 1분 안에 매칭에 진입하는 상황입니다.

```bash
TARGET_TPS=5 DURATION=5m VUS=50 RUN_ID=v1-steady-5tps k6 run docs/load-test/join-queue-steady.js
```

확인할 것:

- `joinQueue` p95가 300ms 아래인지
- 매칭 성공 유저 대기 시간 p95가 steady 목표 1초 미만 또는 허용 2초 미만인지
- 전체 대기 인원이 테스트 종료 후 1분 내 0으로 수렴하는지
- 스캔 소요 시간 p95/p99가 안정적인지

### 2.2 높은 피크: 25 TPS / 5분

피크 동시 접속 사용자 5,000명 중 30%가 1분 안에 매칭에 진입하는 상황입니다.

```bash
TARGET_TPS=25 DURATION=5m VUS=200 RUN_ID=v1-steady-25tps k6 run docs/load-test/join-queue-steady.js
```

확인할 것:

- 5 TPS 대비 `매칭 성공 유저 대기 시간 p95`가 얼마나 증가하는지
- `스캔당 로드 티켓 수` 증가가 `스캔 소요 시간` 증가로 이어지는지
- 큐가 지속적으로 우상향하는지
- API 인스턴스별 요청 분산이 균등한지

### 2.3 이벤트 피크: 50 TPS / 5분

이벤트 시간대 동시 접속 사용자 10,000명 중 30%가 1분 안에 매칭에 진입하는 상황입니다.

```bash
TARGET_TPS=50 DURATION=5m VUS=400 RUN_ID=v1-steady-50tps k6 run docs/load-test/join-queue-steady.js
```

확인할 것:

- `매칭 성공 유저 대기 시간 p95`가 steady 목표 1초 미만 또는 허용 2초 미만인지
- 테스트 종료 후 큐가 1분 내 소진되는지
- `스캔 소요 시간 p95/p99`가 스케줄 주기를 침범하는지
- `초당 매칭 성사 수`가 약 25 pairs/s에 근접하는지

---

## 3. 보조 시나리오

### 3.1 총 인원 기준 steady 테스트

기존처럼 총 유저 수를 지정 시간 동안 분산 유입할 수도 있습니다. 이 방식은 TPS 목표가 아니라 특정 규모의 큐 처리 상태를 확인하는 보조 테스트입니다.

1,000명 / 5분:

```bash
TOTAL_USERS=1000 VUS=100 RUN_ID=steady-1000 k6 run docs/load-test/join-queue-steady.js
```

5,000명 / 5분:

```bash
TOTAL_USERS=5000 VUS=300 RUN_ID=steady-5000 k6 run docs/load-test/join-queue-steady.js
```

10,000명 / 5분:

```bash
TOTAL_USERS=10000 VUS=500 RUN_ID=steady-10000 k6 run docs/load-test/join-queue-steady.js
```

### 3.2 순간 유입 burst 테스트

`burst`는 TPS 지속 테스트가 아니라, 총 N명을 가능한 빠르게 유입시키는 순간 피크 테스트입니다.

1,000명 burst:

```bash
TOTAL_USERS=1000 VUS=100 RUN_ID=burst-1000 k6 run docs/load-test/join-queue-burst.js
```

5,000명 burst:

```bash
TOTAL_USERS=5000 VUS=500 RUN_ID=burst-5000 k6 run docs/load-test/join-queue-burst.js
```

10,000명 burst:

```bash
TOTAL_USERS=10000 VUS=1000 RUN_ID=burst-10000 k6 run docs/load-test/join-queue-burst.js
```

burst에서 볼 지표:

- 큐 최고점
- 큐 소진 시간
- `joinQueue` p95/p99
- `스캔 소요 시간` p95/p99
- `매칭 성공 유저 대기 시간` p95/p99
- 락 스킵 수

---

## 4. 집중해서 볼 지표

### 4.1 1순위: 매칭 성공 유저 대기 시간 p95

사용자가 `joinQueue`에 들어온 뒤 실제 매칭이 성사될 때까지 걸린 시간입니다.

이 값은 `joinQueue`에 들어온 뒤 `match found`가 발생할 때까지의 시간입니다. 매칭 성사 이후 수락/거절 모달 유효 시간 10초와는 별도 정책입니다.

판단 기준:

- steady 목표: p95 < 1초
- steady 허용: p95 < 2초
- burst 목표: p95 < 3초
- burst 허용: p95 < 5초
- p99는 꼬리 지연 확인용으로 기록

### 4.2 2순위: 전체 대기 인원

Redis 매칭 큐에 남아 있는 전체 유저 수입니다.

테스트 중 일시적으로 증가할 수는 있지만, steady 테스트에서는 장기적으로 계속 우상향하면 안 됩니다. 테스트 종료 후 1분 내 0으로 수렴해야 합니다.

### 4.3 3순위: 초당 매칭 성사 수

유저 2명이 1페어가 되므로, 목표 TPS의 절반에 가까운 처리량이 필요합니다.

예:

```text
5 TPS 유입 -> 약 2.5 pairs/s 필요
25 TPS 유입 -> 약 12.5 pairs/s 필요
50 TPS 유입 -> 약 25 pairs/s 필요
```

### 4.4 4순위: 스캔 소요 시간 p95/p99

V1은 전체 티어 대기열을 로드하고 인메모리에서 정렬/매칭합니다. 유저 수가 늘어날수록 이 지표가 먼저 나빠질 가능성이 큽니다.

스캔 시간이 스케줄 주기에 가까워지면 다음 스캔이 밀리고, 큐 적체와 매칭 대기 시간 증가로 이어질 수 있습니다.

### 4.5 5순위: 스캔당 로드 티켓 수

엔진이 한 번에 읽어 온 대기열 크기입니다.

이 값이 커질수록 `스캔 소요 시간`과 같이 봐야 합니다. 티켓 수가 증가하는데 스캔 시간이 비선형으로 증가하면 V1 전체 스캔 구조의 한계로 판단합니다.

### 4.6 보조 지표: joinQueue API 성능

`joinQueue` API는 매칭 엔진과 분리해서 봅니다.

API 지표가 나쁘면 API/Redis write/인증/connection pool을 봐야 하고, API 지표는 정상인데 큐가 쌓이면 매칭 엔진을 봐야 합니다.

---

## 5. 실행 환경

infra를 먼저 실행해 `smite-local` 네트워크와 MySQL/Redis를 준비합니다.

```bash
docker compose -f infra/local/docker-compose-infra.yml up -d
```

API 인스턴스 2개는 app compose만 단독으로 실행할 수 있습니다.

```bash
docker compose -f infra/local/docker-compose-app.yml up -d --build
```

모니터링도 단독 실행할 수 있습니다.

```bash
docker compose -f infra/local/docker-compose-monitoring.yml up -d
```

`docker-compose-app.yml`과 `docker-compose-monitoring.yml`은 `smite-local` external network를 사용하므로 infra가 먼저 올라가 있어야 합니다.

---

## 6. 공통 옵션

API 주소를 바꾸려면 `API_BASE_URLS`를 지정합니다.

```bash
API_BASE_URLS=http://localhost:8080,http://localhost:8081 TARGET_TPS=5 DURATION=5m VUS=50 k6 run docs/load-test/join-queue-steady.js
```

계정 namespace를 분리하고 싶으면 `TEST_USER_NAMESPACE`를 바꾸면 됩니다.

```bash
TEST_USER_NAMESPACE=v1 TARGET_TPS=25 DURATION=5m VUS=200 k6 run docs/load-test/join-queue-steady.js
```

setup 기본 제한 시간은 10분입니다. 더 늘려야 하면 `SETUP_TIMEOUT`을 지정합니다.

```bash
SETUP_TIMEOUT=15m TARGET_TPS=50 DURATION=5m VUS=400 k6 run docs/load-test/join-queue-steady.js
```

setup batch 크기는 기본 250명입니다. API가 준비 단계에서 부담을 받으면 `SETUP_BATCH_SIZE`를 낮추고, 준비 시간을 줄이고 싶으면 높입니다.

```bash
SETUP_BATCH_SIZE=100 TARGET_TPS=50 DURATION=5m VUS=400 k6 run docs/load-test/join-queue-steady.js
```

현재 threshold는 `joinQueue` HTTP 실패율 1% 미만만 둡니다. setup 단계의 `login`, `signUp` 요청은 유저 준비 흐름이므로 threshold 대상에서 제외합니다. `joinQueue` p95/p99는 합격선으로 판정하지 않고 측정값으로 기록합니다.

---

## 7. 테스트 유저 준비 방식

스크립트는 setup 단계에서 테스트 유저를 준비하고 로그인한 뒤, 본 테스트에서 유저별로 1회씩 `joinQueue`를 호출합니다.

같은 유저로 `joinQueue`를 반복 호출하면 `이미 대기 중` 상태가 되어 결과가 왜곡되기 때문입니다.

유저 계정은 `TEST_USER_NAMESPACE` 기준으로 고정됩니다. 같은 namespace로 다시 실행하면 이미 존재하는 유저를 재사용하고, 없는 유저만 생성합니다.

setup 흐름:

```text
login 시도
계정이 있으면 토큰 재사용
계정이 없으면 signUp
signUp 후 다시 login
joinQueue 테스트 실행
```

신규 계정의 첫 `login` 실패(`401/AUTH_008`)는 없는 계정을 확인하는 준비 단계 흐름이므로 테스트 실패로 집계하지 않습니다.

---

## 8. 실행 전 Redis 초기화

각 실행 전 Redis 매칭 키를 초기화해야 같은 유저가 이미 대기 중인 상태로 남지 않습니다.

```bash
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "match:status:*"); do redis-cli del "$key"; done'
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "matching:queue:*"); do redis-cli del "$key"; done'
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "match:session:*"); do redis-cli del "$key"; done'
```

---

## 9. 결과 해석

테스트 결과 해석, Grafana 지표 비교 방식, 버전별 결과 기록은 [분석-가이드.md](./분석-가이드.md)를 따릅니다.

---

## 10. SSE 매칭 알림 부하 테스트

SSE 테스트는 `joinQueue`처럼 짧은 HTTP 요청 성능을 보는 테스트가 아닙니다. 핵심은 **매칭 대기 화면에 들어온 사용자가 SSE 연결을 안정적으로 유지하고, 서버가 `heartbeat`와 `match_found` 이벤트를 지연 없이 보낼 수 있는지** 확인하는 것입니다.

MVC `SseEmitter` V1과 향후 WebFlux/Netty SSE를 같은 기준으로 비교하기 위해 서버 메트릭 이름은 구현체와 무관하게 고정합니다.

### 10.1 SSE 테스트 목표

| 구분 | 동시 SSE 연결 수 | 목적 |
| --- | ---: | --- |
| 기본 안정성 | 1,000 | MVC SSE가 문제 없이 연결을 유지하는지 확인 |
| 목표 안정 구간 | 5,000 | 피크 동접 규모에서 Thread/Heap/CPU가 버티는지 확인 |
| 한계 확인 구간 | 10,000 | Netty 전환 필요성을 판단하기 위한 압박 테스트 |

### 10.2 서버에서 수집하는 SSE 지표

| Prometheus 지표 | 의미 | 비교 목적 |
| --- | --- | --- |
| `sse_notification_connections_active` | 현재 활성 SSE 연결 수 | 목표 연결 수까지 올라가는지 확인 |
| `sse_notification_connections_opened_total` | SSE 연결 생성 수 | 연결 성공 처리량 확인 |
| `sse_notification_connections_closed_total{reason}` | SSE 연결 종료 수와 사유 | timeout/error/send_failure 증가 여부 확인 |
| `sse_notification_connection_duration_seconds` | SSE 연결 유지 시간 | 연결이 테스트 시간만큼 유지되는지 확인 |
| `sse_notification_events_send_attempts_total{event}` | 이벤트 전송 시도 수 | heartbeat/match_found 전송량 확인 |
| `sse_notification_events_send_success_total{event}` | 이벤트 전송 성공 수 | 이벤트 전달 성공률 확인 |
| `sse_notification_events_send_failures_total{event}` | 이벤트 전송 실패 수 | 끊어진 연결, 서버 write 실패 확인 |
| `sse_notification_event_send_duration_seconds{event,result}` | 이벤트 전송 소요 시간 | MVC vs Netty p95/p99 비교 |
| `jvm_threads_live_threads` | JVM live thread 수 | MVC SSE의 thread 사용량 확인 |
| `jvm_threads_states_threads{state}` | JVM thread 상태별 수 | runnable/waiting/timed-waiting 증가 패턴 확인 |
| `tomcat_threads_current_threads` | Tomcat worker thread 현재 수 | MVC SseEmitter 병목 진단 |
| `tomcat_threads_busy_threads` | Tomcat busy thread 수 | MVC 요청 처리 thread 포화 여부 확인 |
| `tomcat_connections_current_connections` | Tomcat 현재 연결 수 | MVC/Tomcat 연결 수 확인 |
| `jvm_memory_used_bytes{area="heap"}` | JVM heap 사용량 | 연결 수 증가에 따른 메모리 증가량 확인 |
| `process_cpu_usage` | API 프로세스 CPU 사용률 | heartbeat/event 전송 비용 확인 |

### 10.3 Grafana 대시보드

SSE 전용 대시보드는 아래 파일을 import해서 사용합니다.

```text
docs/grafana/smite-sse-notification-dashboard.json
```

대시보드에서 가장 먼저 볼 패널은 다음 순서입니다.

1. `활성 SSE 연결 수`
2. `JVM Thread 수`
3. `JVM Thread 상태별 수`
4. `Tomcat Thread 수`
5. `Tomcat 현재 연결 수`
6. `JVM Heap 사용량`
7. `SSE 이벤트 전송 실패율`
8. `SSE 이벤트 전송 지연 시간`
9. `연결 종료 사유`

인스턴스 선택 변수에서 `All`, `8080`, `8081`처럼 전체/개별 서버를 나눠 볼 수 있습니다. 전체 성능은 `All` 기준으로 보고, 특정 서버만 튀는 현상은 instance label 기준으로 따로 분석합니다.

### 10.4 연결 유지 테스트

매칭 대기 화면에 사용자가 머무르면서 SSE 연결만 유지하는 시나리오입니다. `heartbeat` 안정성과 서버 리소스 사용량을 봅니다.

1,000 연결:

```bash
MODE=connection CONNECTIONS=1000 HOLD_DURATION=5m RAMP_UP=60s TEST_USER_NAMESPACE=sse-1000 node docs/load-test/sse-notification-load.mjs
```

5,000 연결:

```bash
MODE=connection CONNECTIONS=5000 HOLD_DURATION=5m RAMP_UP=120s SETUP_CONCURRENCY=150 TEST_USER_NAMESPACE=sse-5000 node docs/load-test/sse-notification-load.mjs
```

10,000 연결:

```bash
MODE=connection CONNECTIONS=10000 HOLD_DURATION=5m RAMP_UP=180s SETUP_CONCURRENCY=200 TEST_USER_NAMESPACE=sse-10000 node docs/load-test/sse-notification-load.mjs
```

확인 기준:

| 지표 | 성공 기준 |
| --- | --- |
| 활성 SSE 연결 수 | 목표 연결 수의 99% 이상 도달 |
| 연결 유지 시간 p95 | `HOLD_DURATION`에 근접 |
| 전송 실패율 | 1% 미만 |
| heartbeat 전송 | 연결 수에 비례해 안정적으로 증가 |
| JVM Thread | 연결 수 증가에 따라 비정상 급증하면 Netty 전환 후보 |
| Heap/CPU | 테스트 종료 후 안정적으로 내려와야 함 |

### 10.5 match_found 이벤트 전송 테스트

SSE 연결을 먼저 열고, 같은 유저들로 `joinQueue`를 호출해 실제 매칭 성사 이벤트가 클라이언트까지 도착하는지 확인하는 시나리오입니다.

1,000명:

```bash
MODE=match CONNECTIONS=1000 HOLD_DURATION=5m RAMP_UP=60s CONNECT_WAIT=70s JOIN_TPS=5 TEST_USER_NAMESPACE=sse-match-1000 node docs/load-test/sse-notification-load.mjs
```

5,000명:

```bash
MODE=match CONNECTIONS=5000 HOLD_DURATION=5m RAMP_UP=120s CONNECT_WAIT=140s JOIN_TPS=25 SETUP_CONCURRENCY=150 TEST_USER_NAMESPACE=sse-match-5000 node docs/load-test/sse-notification-load.mjs
```

10,000명:

```bash
MODE=match CONNECTIONS=10000 HOLD_DURATION=5m RAMP_UP=180s CONNECT_WAIT=210s JOIN_TPS=50 SETUP_CONCURRENCY=200 TEST_USER_NAMESPACE=sse-match-10000 node docs/load-test/sse-notification-load.mjs
```

`CONNECT_WAIT`는 SSE 연결을 충분히 연 뒤 `joinQueue`를 시작하기 위한 대기 시간입니다. 보통 `RAMP_UP`보다 조금 길게 잡습니다.

확인 기준:

| 지표 | 성공 기준 |
| --- | --- |
| `match_found` 전송 성공 수 | 매칭 성사 유저 수에 근접 |
| `match_found` 전송 실패율 | 1% 미만 |
| `match_found` 전송 지연 p95 | steady 상황에서 낮고 안정적이어야 함 |
| 연결 종료 사유 | `send_failure`, `error`, `timeout`이 급증하지 않아야 함 |
| joinQueue/매칭 엔진 지표 | 기존 매칭 대시보드 기준도 함께 만족해야 함 |

### 10.6 MVC SSE vs Netty SSE 비교 기준

V1 MVC `SseEmitter`와 V2 Netty/WebFlux SSE를 비교할 때는 같은 시나리오와 같은 지표를 사용합니다.

| 비교 항목 | 봐야 할 지표 | Netty 전환 판단 |
| --- | --- | --- |
| 연결 수용량 | 활성 SSE 연결 수 | MVC가 목표 연결 수를 안정적으로 유지하지 못하면 전환 검토 |
| Thread 사용량 | JVM Thread 수, JVM Thread 상태별 수 | 연결 수에 비례해 live/waiting/timed-waiting thread가 크게 증가하면 전환 후보 |
| MVC Thread 병목 | Tomcat current/busy threads | busy thread가 높게 유지되면 MVC/Tomcat 병목 가능성 |
| 메모리 비용 | Heap 사용량 | 연결당 메모리 증가량이 높으면 전환 후보 |
| 이벤트 전송 지연 | `sse_notification_event_send_duration_seconds` p95/p99 | p95/p99가 부하에 따라 크게 튀면 전환 후보 |
| 실패율 | 이벤트 전송 실패율, 연결 종료 사유 | `send_failure`, `error`, `timeout`이 증가하면 전환 후보 |
| 회복성 | 테스트 종료 후 active connection/heap/thread 감소 | 종료 후 리소스가 내려오지 않으면 누수 의심 |

정리하면, Netty 전환 여부는 단순히 "10,000명을 목표로 하니까 Netty"가 아니라 아래 질문에 대한 측정 결과로 판단합니다.

```text
MVC SseEmitter가 10,000 연결에서 thread, heap, CPU, 이벤트 전송 p95/p99를 안정적으로 유지하는가?
```

유지하지 못하면 WebFlux/Netty로 전환하고, 같은 `sse_notification_*` 지표로 개선 폭을 비교합니다.

주의할 점은 `tomcat_*` 지표는 MVC/Tomcat 진단용이라는 것입니다. Netty/WebFlux로 전환하면 Tomcat 지표는 비교 대상에서 빠질 수 있습니다. 따라서 버전 간 핵심 비교는 `sse_notification_*`, `jvm_threads_*`, `jvm_memory_*`, `process_cpu_usage`를 기준으로 하고, Tomcat 지표는 MVC V1 병목 원인 분석에 사용합니다.
