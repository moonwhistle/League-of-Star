# Issue-22: Match Module Separation

## 📌 개요 (Overview)
- **English**: Separate matching logic into a dedicated module and establish a scalable foundation using Redisson.
- **Korean**: 매칭 로직을 전용 모듈(`smite-matching`)로 분리하고, Redisson을 활용하여 멀티 서버 환경에서도 안전하게 동작하는 매칭 기반을 구축합니다.

## 📚 작업 순서 (Tasks)

### 1. 모듈 생성 및 환경 설정
- [ ] **smite-matching 멀티 모듈 생성**: 루트 `settings.gradle`에 등록 및 디렉터리 구조 생성.
- [ ] **build.gradle 설정**: 
    - `smite-core` 및 `smite-infra-redis` 의존성 추가.
    - `redisson-spring-boot-starter` 의존성 추가.
- [ ] **Redisson 설정**: 멀티 서버 환경에서 공유할 수 있는 Redisson Client 설정 클래스 구현.

### 2. 도메인 추상화 (smite-core)
- [ ] **매칭 도메인 모델 정의**: `MatchTicket`(대기 정보), `MatchWindow`(탐색 범위) 등 정의.
- [ ] **MatchStore 인터페이스 정의**: 인프라 기술(Redis)에 의존하지 않는 대기열 조작 인터페이스 정의.

### 3. Redis 기반 대기열 구현 (smite-matching)
- [ ] **ZSET 기반 대기열 로직**: 유저의 티어 점수를 Score로 하는 Redis Sorted Set 진입/취소 기능 구현.
- [ ] **MatchStore 구현체 작성**: Redisson을 활용한 원자적 대기열 조작 로직 구현.

### 4. 분산 락 및 엔진 기초 (Issue-22 범위 내)
- [ ] **Redisson Lock 적용**: 매칭 조작 시 동시성 문제를 방지하기 위한 분산 락 유틸리티 구현.
- [ ] **기초 테스트 코드**: Redis Testcontainers를 활용한 대기열 진입/추출 정합성 테스트.

## 📝 기술적 의사결정 (Technical Decisions)

### 왜 Redisson 분산 락인가?
- **확장성**: 동시 접속자가 늘어나 API 서버를 여러 대 띄우더라도, 동일한 유저가 중복 매칭되는 현상을 원천 차단할 수 있습니다.
- **안정성**: Redis Lua Script보다 자바 코드 가독성이 높으며, 락 만료 시간(Lease Time) 관리가 자동화되어 있어 서버 장애 시 데드락 위험이 낮습니다.

### 매칭 탐색 방식
- 이번 이슈에서는 **Redis 상태(ZSET) 기반의 진입/추출**이라는 기본기에 집중합니다. 
- 이후 이슈에서 이 대기열을 주기적으로 스캔하여 짝을 맺어주는 `MatchEngine(Worker)` 로직을 고도화할 예정입니다.

## 📌 관련 이슈
- Closes #22
