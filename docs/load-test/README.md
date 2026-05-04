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
