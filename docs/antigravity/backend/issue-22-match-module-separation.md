# Issue-22: Match Module Separation

## 📌 Summary

`league-of-star-matching` 전용 모듈을 생성하고, Redis(Redisson) 기반의 티어별 분할 ZSET 대기열 저장소(`RedisMatchStore`)를 구현했습니다.
또한 모듈 간 관심사 분리 원칙을 정리하고, 테스트 인프라를 각 모듈 독립 구조로 리팩터링했습니다.

---

## 📚 Changes

### 1. 모듈 생성 및 환경 설정
- [x] **league-of-star-matching 멀티 모듈 생성**: 루트 `settings.gradle`에 등록 및 디렉터리 구조 생성 완료.
- [x] **build.gradle 설정**:
    - `league-of-star-core` 및 `league-of-star-infra-redis` 의존성 추가 완료.
    - `redisson-spring-boot-starter` 의존성 추가 완료.
- [x] **Redisson 설정**: Spring Boot 설정을 참조하여 RedissonClient를 Bean으로 등록 완료.

### 2. 도메인 추상화 (league-of-star-core)
- [x] **매칭 도메인 모델 정의**: `MatchTicket`(대기 정보), `MatchStatus`(프로세스 상태) 정의 완료.

### 3. Redis 기반 대기열 구현 (league-of-star-matching)
- [x] **MatchStore 인터페이스 정의**: `league-of-star-matching` 모듈 내 `repository` 패키지에 위치. 인프라 기술에 의존하지 않는 대기열 조작 추상화 계층.
    - `core`가 아닌 `matching` 모듈에 두는 것이 적절: `api`는 `matching`을 의존하므로 접근 가능하며, 매칭 관련 지식이 `matching` 모듈에 캡슐화됨.
- [x] **RedisMatchStore 구현체 작성**: Redisson을 활용한 원자적 대기열 조작 로직 구현.
    - `add()`: 티어 점수를 Score로, userId를 Member로 ZADD
    - `remove()`: 매칭 취소 시 ZREM
    - `findAll()`: Batch 파이프라이닝으로 전체 티어 큐 조회
    - `atomicPairRemove()`: Lua 스크립트로 두 유저를 다른 키에서 원자적 제거
- [x] **Lua 스크립트 분리**: `atomic_pair_remove.lua`를 `resources/scripts/`에 별도 관리.

### 4. 분산 락 (league-of-star-infra-redis)
- [x] **DistributedLockAop**: `@DistributedLock` 어노테이션 기반 AOP로 분산 락 유틸리티 구현. 모든 모듈에서 공통 사용 가능.

### 5. 테스트 인프라 리팩터링
- [x] **AbstractRedisTest**: `league-of-star-infra-redis` testFixtures에 위치. Testcontainers 기반 Redis를 띄우는 순수 인프라 베이스 클래스.
- [x] **모듈별 독립 테스트 컨텍스트**: 각 모듈(matching, api)이 자체 `@SpringBootTest` 컨텍스트를 가지도록 분리하여 JPA 등 불필요한 빈 스캔 제거.
- [x] **Redis 초기화 통일**: `@AfterEach`에서 `RedisConnectionFactory.flushAll()` 방식으로 통일 (라이브러리 의존성 없이 스프링 표준 사용).

### 6. 레디스 레포지토리 스캔 구조 개선 (league-of-star-api)
- [x] **`@RedisRepository` 커스텀 어노테이션**: `league-of-star-api/global/annotation`에 생성. 어노테이션 기반 자동 스캔으로 경로를 수동 추가할 필요 없음.
- [x] **`RedisRepositoryConfig` 개선**: `basePackages = "com.sang.leagueofstar"` + `includeFilters`로 전체 패키지에서 `@RedisRepository`가 붙은 인터페이스만 등록.

---

## 📝 Note

### 아키텍처 결정: `MatchStore` 위치
- `core` 모듈이 아닌 `matching` 모듈에 인터페이스를 위치시킴.
- `league-of-star-api → league-of-star-matching` 의존성이 이미 있으므로 `api`에서 접근 가능.
- 매칭 도메인 지식을 `matching` 모듈에 캡슐화하는 것이 응집도 관점에서 적합.

### `joinQueue()` 동기 처리 결정
- Redis ZADD는 마이크로초 단위로 빠르므로 비동기 처리 불필요.
- 비동기 시 Redis 쓰기 실패를 유저에게 전달할 수 없는 위험 존재.
- **매칭 탐색(`MatchEngine`)과 결과 알림(WebSocket)만 비동기**로 처리하는 것이 표준 패턴.

---

## 🔜 Next Issue (다음 이슈로 이관)

### 매칭 진입 실패 처리 (이슈 분리)
아래 항목들은 이번 이슈 범위에서 제외하고 별도 이슈로 진행 예정.

- [ ] **`MatchUserStatusStore`**: `match:status:{userId}` 키로 유저의 현재 매칭 상태(MATCHING / FOUND / IN_GAME 등)를 Redis에 관리.
- [ ] **중복 진입 방지**: `joinQueue()` 호출 시 유저가 이미 `MATCHING` 상태이면 예외 발생.
- [ ] **`MatchService.joinQueue()` 구현**: 상태 확인 → 대기열 등록 → 상태 변경 트랜잭션 설계.
- [ ] **`MatchEngine` (백그라운드 스케줄러)**: 1초 주기로 대기열을 스캔하고 슬라이딩 윈도우 알고리즘으로 짝 매칭.
- [ ] **`MatchFoundEvent` 발행**: 매칭 성사 시 이벤트 발행 (WebSocket 알림은 그 다음 이슈).

---

## 📌 Related Issue
- Closes #22
