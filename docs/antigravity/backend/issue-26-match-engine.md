# Issue-26: 매칭 엔진 구현 (Match Engine - V1)

## 📌 Feature Description

Redis ZSET 대기열에 진입한 유저들을 주기적으로 스캔하여 조건에 맞는 두 명을 매칭시키는 매칭 엔진(Worker)을 구현합니다.
V1 스펙(`matching-v1.md`)에 따라, **모든 티어의 대기열을 인메모리로 일괄 로드하여 가장 오래 대기한 유저부터 매칭을 시도하는 FIFO 기반의 통합 매칭**을 수행합니다. 대기 시간에 따라 매칭 허용 티어 범위를 점진적으로 넓히는 슬라이딩 윈도우(Sliding Window) 정책을 적용합니다.

유저의 자진 취소(leaveQueue)와 엔진의 선점 간 동시성 충돌을 방지하기 위해 `atomic_pair_remove.lua` 스크립트를 활용하여 원자적으로 큐에서 유저를 추출합니다.

---

## 📚 Tasks

### 1. 매칭 엔진 스케줄러 기반 구축 (smite-matching)
- [x] **스케줄러 설정 (`@EnableScheduling`)**
  - 단일 스레드 병목을 막기 위해 전용 `ThreadPoolTaskScheduler` 설정
- [x] **`MatchEngine` 클래스 생성**
  - `@Scheduled` 어노테이션을 사용하여 주기적(예: 1초 간격)으로 실행되는 `processMatching()` 워커 스레드 구성

### 2. 글로벌 분산 락 적용 (중복 스캔 방지)
- [x] **전역 스캔 락(Lock) 적용**
  - 멀티 인스턴스 환경에서 여러 엔진이 동시에 전체 큐를 스캔하는 것을 막기 위해 `Redisson` 전역 분산 락 사용
  - 매칭 엔진은 Redis-only 작업이므로 `@DistributedLock` AOP 대신 `RedissonClient.getLock()` + `RLock.tryLock()`을 직접 사용
  - Lock Key: `lock:match:engine`
  - WaitTime 0초: 다른 인스턴스가 스캔 중이면 이번 사이클은 즉시 스킵
  - LeaseTime 5초: 엔진 장애 시 락이 고립되지 않도록 스케줄링 간격보다 길게 설정
  - 락 획득 중 인터럽트 발생 시 interrupt 상태를 복원하고 해당 사이클을 종료

### 3. 전체 데이터 로드 및 FIFO 인메모리 정렬
- [x] **전체 대기열 일괄 조회 (`MatchStore.findAll`)**
  - Redis 파이프라이닝을 활용해 1~28티어의 모든 `matching:queue:*` 데이터를 한 번에 가져와 `List<MatchTicket>`으로 병합
- [x] **대기 시간 기준 정렬 (FIFO)**
  - 통합된 리스트를 `entryTime` (대기열 진입 시간) 오름차순으로 정렬하여 가장 오래 기다린 사람에게 우선권 부여

### 4. 매칭 범위 확장(Sliding Window) 페어링 로직
- [x] **대기 시간에 따른 허용 티어 폭 계산 로직**
  - 0~10초: ±1 티어
  - 11~20초: ±2 티어
  - 21~30초: ±4 티어
  - 31초 이상: ±8 티어 (최대 확장)
- [x] **후보 탐색 및 원자적 추출 연동 (`atomicPairRemove`)**
  - 정렬된 리스트를 순회하며 기준 유저의 허용 폭 내에 있는 상대를 찾음
  - 상대가 발견되면 `atomicPairRemove` Lua 스크립트를 호출하여 서로 다른 두 큐(`KEYS[1]`, `KEYS[2]`)에서 동시 제거 시도
  - 결과값이 1(성공)인 경우 매칭 성사 처리, 0인 경우 패스(취소된 유저)

### 5. 매칭 성사 후 상태 변경 처리
- [x] **관심사 분리**
  - `MatchEngine`: 스케줄링 및 전역 락 획득/해제만 담당
  - `MatchEngineService`: 전체 큐 조회, FIFO 정렬, 슬라이딩 윈도우 페어링, `atomicPairRemove` 연동 담당
  - `MatchFoundProcessor`: 매칭 성사 후 상태 변경, 세션 생성, 이벤트 발행 담당
- [x] **유저 상태 변경 (`UserStatusStore`)**
  - `atomicPairRemove` 성공 후 두 유저의 상태를 `MATCHING`에서 `FOUND`로 변경
  - 상태 변경은 `MatchStatus` enum 기준을 따른다 (`GAME_READY` 상태는 사용하지 않음)
- [x] **매칭 수락 세션 생성 (`match:session:{matchId}`)**
  - 매칭 성사 시 고유한 `matchId`를 생성하고 Redis Hash에 수락 세션을 저장
  - 필드: `matchId`, `userA`, `userB`, `status`, `createdAt`
  - 클라이언트에 노출되는 매칭 수락 제한 시간은 10초로 유지
  - Redis 세션 TTL은 네트워크/스케줄링 경계 버퍼를 포함하여 12초로 설정
  - 서버는 세션 존재 여부만 믿지 않고 `createdAt + 10초` 기준으로 수락 유효성을 판정
  - 세션 상태는 양쪽 유저의 수락/거절/타임아웃 처리를 위한 단일 기준으로 사용
- [x] **매칭 결과 발행 (Event/Message)**
  - `MatchFoundEvent`를 발행하여 이후 로직(WebSocket 알림 등)과 느슨하게 결합될 수 있도록 연동 마련
  - 이벤트 payload에는 `matchId`, `userA`, `userB`, `acceptTimeoutSeconds`를 포함
- [x] **후처리 실패 로그**
  - `atomicPairRemove` 성공 후 후처리 중 예외가 발생하면 매칭 대상 유저 ID와 함께 에러 로그를 남김
  - Redis 상태/세션 생성 원자화 및 이벤트 재시도는 후속 이슈에서 검토

### 6. 매칭 엔진 모니터링 지표 추가 (Metrics)
- [x] **엔진 스캔 레이턴시 수집 (Timer)**
  - `match_engine_scan_duration_seconds`: 엔진이 전체 큐를 스캔하고 인메모리 매칭을 완료하는 데 걸린 시간
- [x] **스캔 입력 크기 수집 (DistributionSummary)**
  - `match_engine_scan_tickets`: 엔진 1회 스캔에서 로드한 티켓 수
- [x] **매칭 성사 처리량 수집 (Counter)**
  - `match_engine_pairs_total`: 성공적으로 성사된 페어 수 (TPS 측정용)
- [x] **스캔당 페어 수 수집 (DistributionSummary)**
  - `match_engine_pairs_per_scan`: 엔진 1회 스캔에서 성사된 페어 수
- [x] **Lua 원자 제거 경합 지표 수집 (Counter)**
  - `match_engine_atomic_pair_attempts_total`: `atomicPairRemove` 시도 수
  - `match_engine_atomic_pair_failures_total`: `atomicPairRemove` 실패 수
- [x] **매칭된 유저 대기 시간 수집 (Timer)**
  - `match_engine_matched_user_wait_duration_seconds`: 매칭 성공 유저가 큐에서 대기한 시간
- [x] **락 경합 지표 수집 (Counter)**
  - `match_engine_lock_skipped_total`: 다른 인스턴스가 락을 보유해 스킵된 스캔 수
- [x] **대기열 크기 Gauge 최적화**
  - `match_queue_size`: 전체 큐 스캔 대신 티어별 ZSET 크기 조회로 측정
- [x] **Grafana 대시보드 갱신**
  - 매칭 엔진 개선 비교용 지표 중심으로 `docs/grafana/smite-match-queue-dashboard.json` 구성

### 7. 테스트 코드 작성
- [x] **`MatchEngineServiceTest` 단위 테스트 작성**
  - Mock: `MatchStore`, `MatchFoundService`, `MatchEngineMetrics`
  - 대기 인원 0~1명일 때 `atomicPairRemove`와 후처리가 호출되지 않는지 검증
  - `entryTime` 오름차순 FIFO 정렬 후 가장 오래 기다린 유저부터 후보를 찾는지 검증
  - Sliding Window 검증:
    - 0~10초 대기: ±1 티어만 매칭
    - 11~20초 대기: ±2 티어까지 매칭
    - 21~30초 대기: ±4 티어까지 매칭
    - 31초 이상 대기: ±8 티어까지 매칭
  - `atomicPairRemove`가 `false`를 반환하면 후처리(`MatchFoundService.process`)가 호출되지 않고 다음 후보/유저로 진행되는지 검증
  - 한 스캔에서 동일 유저가 두 번 매칭되지 않는지 검증
  - 매칭 성공 시 `MatchEngineMetrics`의 스캔 시간, 스캔 티켓 수, 스캔당 페어 수, 원자 제거 시도/실패, 매칭 대기 시간이 기록되는지 검증

- [x] **`MatchEngineTest` 단위 테스트 작성**
  - Mock: `RedissonClient`, `RLock`, `MatchEngineService`, `MatchEngineMetrics`
  - 락 획득 성공 시 `MatchEngineService.processMatching()`이 1회 호출되고 finally에서 unlock 되는지 검증
  - 락 획득 실패 시 엔진 서비스는 호출되지 않고 `incrementLockSkipped()`가 호출되는지 검증
  - `tryLock` 중 `InterruptedException` 발생 시 interrupt 상태를 복원하고 unlock을 시도하지 않는지 검증

- [x] **`RedisMatchStoreTest` 통합 테스트 보강**
  - `countByTierScore(tierScore)`가 해당 티어 ZSET 크기만 정확히 반환하는지 검증
  - `atomicPairRemove`가 서로 다른 티어 큐의 두 유저를 원자적으로 제거하는지 검증
  - 한 유저가 이미 취소되어 큐에 없으면 `atomicPairRemove`가 `false`를 반환하고 남은 유저를 제거하지 않는지 검증

- [x] **`RedisMatchSessionStoreTest` 통합 테스트 작성**
  - `save()` 시 `match:session:{matchId}`가 Redis Hash 필드(`matchId`, `userA`, `userB`, `status`, `createdAt`)로 저장되는지 검증
  - `findById()`가 Redis Hash를 `MatchSession`으로 복원하는지 검증
  - 세션 TTL이 12초로 적용되는지 검증
  - `delete()` 호출 시 세션이 제거되는지 검증

- [x] **`MatchFoundServiceTest` 단위 테스트 작성**
  - 매칭 성사 시 두 유저 상태가 `FOUND`로 변경되는지 검증
  - `MatchSessionStore.save()`가 TTL 12초로 호출되는지 검증
  - `MatchFoundEvent`가 `matchId`, `userA`, `userB`, `acceptTimeoutSeconds=10`을 포함해 발행되는지 검증

- [x] **`MatchEngineMetricsTest` 단위 테스트 작성**
  - `SimpleMeterRegistry` 기반으로 엔진 지표가 의도한 meter name으로 기록되는지 검증
  - 검증 대상:
    - `match.engine.scan.duration`
    - `match.engine.scan.tickets`
    - `match.engine.pairs`
    - `match.engine.pairs.per.scan`
    - `match.engine.atomic_pair.attempts`
    - `match.engine.atomic_pair.failures`
    - `match.engine.matched_user.wait.duration`
    - `match.engine.lock.skipped`

### 8. 매칭 엔진 부하 테스트
- [ ] **부하 테스트 목적 정의**
  - 매칭 엔진 개선 전/후를 비교할 수 있도록 동일 조건에서 1,000명, 5,000명, 10,000명 대기열 진입 시나리오를 반복 측정
  - API 인스턴스 2개가 같은 Redis 대기열을 공유할 때 중복 매칭, 락 경합, 스캔 지연, 매칭 대기 시간이 허용 범위 안에 있는지 확인
  - `joinQueue` API 처리 성능과 매칭 엔진 처리 성능을 분리해서 관측

- [ ] **로컬 부하 테스트 실행 환경 구성**
  - `infra/local/docker-compose-infra.yml`로 MySQL, Redis 실행
  - `infra/local/docker-compose-monitoring.yml`로 Prometheus, Grafana 실행
  - `smite-api` 인스턴스 2개 실행
    - Instance A: `server.port=8080`
    - Instance B: `server.port=8081`
    - 두 인스턴스는 동일한 MySQL/Redis를 바라보게 구성
  - Prometheus scrape target을 두 인스턴스로 확장
    - `smite-api-1:8080`
    - `smite-api-2:8080`
  - 부하 도구는 `k6`를 우선 사용하고, 스크립트는 `docs/load-test` 또는 별도 load-test 디렉터리에 보관

- [ ] **테스트 데이터 준비**
  - 1,000명, 5,000명, 10,000명 규모별 테스트 유저 생성 방식 정의
  - 각 테스트 유저가 인증된 `joinQueue` 요청을 보낼 수 있도록 JWT 발급 또는 테스트 전용 인증 우회 전략 결정
  - 티어 분포는 편향 없이 매칭 엔진을 검증할 수 있도록 1~28 구간에 균등 분산
  - 각 시나리오 시작 전 Redis 매칭 데이터 초기화
    - `match:status:*`
    - `matching:queue:*`
    - `match:session:*`

- [ ] **부하 테스트 시나리오 작성**
  - Scenario A: 1,000명 `POST /api/v1/match/join`
    - 목표: 기본 부하에서 매칭 엔진이 정상적으로 큐를 소진하는지 확인
  - Scenario B: 5,000명 `POST /api/v1/match/join`
    - 목표: 스캔 입력 크기 증가에 따른 p95 스캔 시간, p95 매칭 대기 시간 변화 확인
  - Scenario C: 10,000명 `POST /api/v1/match/join`
    - 목표: V1 인메모리 전체 스캔 방식의 한계 지점과 Redis/Lua 경합 수준 확인
  - 각 시나리오는 같은 조건으로 최소 3회 반복 실행하고 평균, p95, 최댓값을 기록

- [ ] **관측 지표 정의**
  - API 지표
    - `POST /api/v1/match/join` 처리량
    - `POST /api/v1/match/join` p95 응답 시간
    - HTTP 2xx/4xx/5xx 비율
  - 매칭 엔진 지표
    - `match_engine_scan_duration_seconds` p95
    - `match_engine_scan_tickets` p95
    - `match_engine_pairs_total` 증가율
    - `match_engine_pairs_per_scan` p95
    - `match_engine_atomic_pair_attempts_total`
    - `match_engine_atomic_pair_failures_total`
    - `match_engine_matched_user_wait_duration_seconds` p95
    - `match_engine_lock_skipped_total`
    - `match_queue_size` 감소 추이
  - 인프라 지표
    - Redis CPU/메모리 사용량
    - Redis command 처리량
    - API JVM heap, GC pause, thread 상태

- [ ] **성공 기준 정의**
  - 모든 시나리오에서 중복 매칭이 없어야 함
  - `match_queue_size`가 테스트 종료 후 기대치까지 감소해야 함
    - 짝수 인원 기준 최종 잔여 큐 0명
    - 후처리 실패가 발생한 경우 실패 로그와 잔여 큐를 함께 분석
  - `match_engine_atomic_pair_failures_total`은 취소/경합이 없는 join-only 시나리오에서 0에 가까워야 함
  - `match_engine_lock_skipped_total`은 멀티 인스턴스 환경에서 발생할 수 있으나, 스캔 지연이나 큐 적체로 이어지는지 함께 판단
  - 10,000명 시나리오에서 p95 매칭 대기 시간이 수락 모달 시간(10초)을 침범하는지 확인하고, 침범 시 V2 최적화 후보로 기록

- [ ] **결과 기록 및 개선 판단**
  - 1,000명, 5,000명, 10,000명 각각의 실행 결과를 표로 정리
  - Grafana 대시보드 캡처 또는 주요 Prometheus 쿼리 결과를 함께 보관
  - 병목이 API 요청 처리인지, Redis 조회/삭제인지, 엔진 인메모리 페어링인지 구분
  - 개선 후보를 후속 이슈로 분리
    - 티어별 병렬 스캔
    - 큐 조회 범위 축소
    - 엔진 전용 독립 worker 분리
    - 매칭 후처리 이벤트 재시도/보상 처리
