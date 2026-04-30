# 매칭 부하 테스트

두 API 인스턴스(`8080`, `8081`)에 요청을 분산해 `POST /api/v1/match/join` 성능을 측정합니다.

스크립트는 setup 단계에서 테스트 유저를 생성하고 로그인한 뒤, 본 테스트에서 유저별로 1회씩 `joinQueue`를 호출합니다. 같은 유저로 `joinQueue`를 반복 호출하면 `이미 대기 중` 상태가 되어 결과가 왜곡되기 때문입니다.

테스트 결과 해석, Grafana 지표 비교 방식, 1,000명/5,000명/10,000명 기준 확장 판단은 [분석-가이드.md](./분석-가이드.md)를 따릅니다.

## 실행 환경

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

## 시나리오 선택

### 1. 지속 유입 테스트

운영 중 유저가 일정 시간 동안 꾸준히 매칭을 누르는 상황을 봅니다. 기본 실행 시간은 3분입니다.

확인할 지표:
- `joinQueue` p95/p99 응답 시간
- HTTP 4xx/5xx 비율
- `match_queue_size`가 장기적으로 증가하는지
- `match_engine_matched_user_wait_duration_seconds` p95
- `match_engine_scan_duration_seconds` p95

1,000명 / 3분:

```bash
TOTAL_USERS=1000 VUS=100 RUN_ID=steady-1000 k6 run docs/load-test/join-queue-steady.js
```

5,000명 / 3분:

```bash
TOTAL_USERS=5000 VUS=300 RUN_ID=steady-5000 k6 run docs/load-test/join-queue-steady.js
```

10,000명 / 3분:

```bash
TOTAL_USERS=10000 VUS=500 RUN_ID=steady-10000 k6 run docs/load-test/join-queue-steady.js
```

실행 시간을 바꾸려면 `DURATION`을 지정합니다.

```bash
DURATION=10m TOTAL_USERS=10000 VUS=300 RUN_ID=steady-10000-10m k6 run docs/load-test/join-queue-steady.js
```

분당 요청 수를 직접 지정하려면 `RATE_PER_MINUTE`를 사용합니다.

```bash
DURATION=3m RATE_PER_MINUTE=500 VUS=100 RUN_ID=steady-rate-500 k6 run docs/load-test/join-queue-steady.js
```

### 2. 순간 피크 테스트

특정 순간에 유저가 몰려 거의 동시에 매칭을 누르는 상황을 봅니다. 장애 경계와 큐 소진 시간을 확인하는 목적입니다.

확인할 지표:
- `joinQueue` p95/p99 응답 시간
- `match_queue_size`가 0까지 줄어드는 데 걸린 시간
- `match_engine_scan_tickets` 최대값
- `match_engine_scan_duration_seconds` p95/p99
- `match_engine_lock_skipped_total`
- `match_engine_atomic_pair_failures_total`

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

## 공통 옵션

API 주소를 바꾸려면 `API_BASE_URLS`를 지정합니다.

```bash
API_BASE_URLS=http://localhost:8080,http://localhost:8081 TOTAL_USERS=1000 VUS=100 k6 run docs/load-test/join-queue-steady.js
```

현재 threshold는 HTTP 실패율 1% 미만만 둡니다. `joinQueue` p95/p99는 합격선으로 판정하지 않고 측정값으로 기록합니다.

## 실행 전 초기화

각 실행 전 Redis 매칭 키를 초기화해야 같은 유저가 이미 대기 중인 상태로 남지 않습니다.

```bash
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "match:status:*"); do redis-cli del "$key"; done'
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "matching:queue:*"); do redis-cli del "$key"; done'
docker exec smite-redis sh -c 'for key in $(redis-cli --scan --pattern "match:session:*"); do redis-cli del "$key"; done'
```
