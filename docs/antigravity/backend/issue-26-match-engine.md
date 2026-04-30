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
  - LeaseTime 2초: 엔진 장애 시 락이 고립되지 않도록 스케줄링 간격보다 길게 설정
  - 락 획득 중 인터럽트 발생 시 interrupt 상태를 복원하고 해당 사이클을 종료

### 3. 전체 데이터 로드 및 FIFO 인메모리 정렬
- [ ] **전체 대기열 일괄 조회 (`MatchStore.findAll`)**
  - Redis 파이프라이닝을 활용해 1~28티어의 모든 `matching:queue:*` 데이터를 한 번에 가져와 `List<MatchTicket>`으로 병합
- [ ] **대기 시간 기준 정렬 (FIFO)**
  - 통합된 리스트를 `entryTime` (대기열 진입 시간) 오름차순으로 정렬하여 가장 오래 기다린 사람에게 우선권 부여

### 4. 매칭 범위 확장(Sliding Window) 페어링 로직
- [ ] **대기 시간에 따른 허용 티어 폭 계산 로직**
  - 0~10초: ±1 티어
  - 11~20초: ±2 티어
  - 21~30초: ±4 티어
  - 31초 이상: ±8 티어 (최대 확장)
- [ ] **후보 탐색 및 원자적 추출 연동 (`atomicPairRemove`)**
  - 정렬된 리스트를 순회하며 기준 유저의 허용 폭 내에 있는 상대를 찾음
  - 상대가 발견되면 `atomicPairRemove` Lua 스크립트를 호출하여 서로 다른 두 큐(`KEYS[1]`, `KEYS[2]`)에서 동시 제거 시도
  - 결과값이 1(성공)인 경우 매칭 성사 처리, 0인 경우 패스(취소된 유저)

### 5. 매칭 성사 후 상태 변경 처리
- [ ] **유저 상태 변경 (`UserStatusStore`)**
  - `atomicPairRemove` 성공 후 두 유저의 상태를 `MATCHING`에서 `FOUND`로 변경
  - 상태 변경은 `MatchStatus` enum 기준을 따른다 (`GAME_READY` 상태는 사용하지 않음)
- [ ] **매칭 수락 세션 생성 (`match:session:{matchId}`)**
  - 매칭 성사 시 고유한 `matchId`를 생성하고 Redis Hash에 수락 세션을 저장
  - 필드: `matchId`, `userA`, `userB`, `status`, `createdAt`
  - 클라이언트에 노출되는 매칭 수락 제한 시간은 10초로 유지
  - Redis 세션 TTL은 네트워크/스케줄링 경계 버퍼를 포함하여 12초로 설정
  - 서버는 세션 존재 여부만 믿지 않고 `createdAt + 10초` 기준으로 수락 유효성을 판정
  - 세션 상태는 양쪽 유저의 수락/거절/타임아웃 처리를 위한 단일 기준으로 사용
- [ ] **매칭 결과 발행 (Event/Message)**
  - `MatchFoundEvent`를 발행하여 이후 로직(WebSocket 알림 등)과 느슨하게 결합될 수 있도록 연동 마련
  - 이벤트 payload에는 `matchId`, `userA`, `userB`, `acceptTimeoutSeconds`를 포함

### 6. 매칭 엔진 모니터링 지표 추가 (Metrics)
- [ ] **엔진 스캔 레이턴시 수집 (Timer)**
  - `match_engine_scan_duration_seconds`: 엔진이 전체 큐를 스캔하고 인메모리 매칭을 완료하는 데 걸린 시간
- [ ] **매칭 성사 처리량 (Counter)**
  - `match_engine_paired_total`: 성공적으로 성사된 페어 수 (TPS 측정용)

### 7. 테스트 코드 작성
- [ ] 대기 시간에 따른 티어 범위 확장이 정상적으로 동작하는지(Sliding Window) 단위 테스트
- [ ] 멀티 스레드/서버 환경에서 글로벌 분산 락이 의도대로 동작하는지 테스트
- [ ] `atomicPairRemove` 스크립트를 통한 이기종 티어 간 교차 제거(Cross-Tier) 연동 테스트
- [ ] 매칭 성사 시 `match:session:{matchId}`가 생성되고 12초 TTL이 적용되는지 테스트
- [ ] 수락 유효 시간은 `createdAt + 10초` 기준으로 판정되는지 테스트
- [ ] 매칭 성사 시 두 유저 상태가 `FOUND`로 변경되고 `MatchFoundEvent`가 발행되는지 테스트
