# Issue-24: Match Queue Join/Leave API

## 📌 Feature Description

유저가 매칭 대기열에 **진입**하고 **취소**하는 API를 구현합니다.
Redis로 유저의 현재 매칭 상태를 관리하여 중복 진입을 방지하고,
대기열 진입/취소의 비즈니스 로직을 `MatchService`로 캡슐화합니다.

---

## 📚 Tasks

### 1. 유저 매칭 상태 관리 (smite-matching)

- [x] **`MatchUserStatusStore` 인터페이스 정의** (`matching/repository/`)
    - `setStatus(Long userId, MatchStatus status, long ttlSeconds)`: 상태 저장
    - `getStatus(Long userId)`: 현재 상태 조회
    - `removeStatus(Long userId)`: 상태 초기화

- [x] **`RedisMatchUserStatusStore` 구현체** (`matching/infrastructure/`)
    - Key: `match:status:{userId}` (String)
    - TTL: 30분 (무한 대기 방지 — 서버 장애 시 자동 만료)
    - 구현 기술: `StringRedisTemplate` 또는 `RedissonClient`

### 2. MatchService 구현 (smite-matching)

- [x] **`MatchService` 클래스** (`matching/service/`)
    - `joinQueue(Long userId, int tierScore)`: 대기열 진입
        1. 현재 상태 조회 → `MATCHING` / `IN_GAME`이면 예외 발생
        2. `MatchStore.add(ticket)` 호출
        3. 상태를 `MATCHING`으로 변경
    - `leaveQueue(Long userId, int tierScore)`: 대기열 취소
        1. 현재 상태가 `MATCHING`이 아니면 예외 발생
        2. `MatchStore.remove(userId, tierScore)` 호출
        3. 상태 초기화(`removeStatus`)

### 3. 에러 코드 추가 (smite-matching)

- [x] **`MatchingErrorCode` 추가**
    - `ALREADY_IN_QUEUE`: 이미 매칭 대기열에 있는 유저
    - `NOT_IN_QUEUE`: 대기열에 없는 유저가 취소 시도

### 4. API 엔드포인트 (smite-api)

- [ ] **`MatchController`** (`api/match/controller/`)
    - `POST /api/v1/match/join`: 매칭 대기열 진입
        - 인증 필요 (JWT)
        - 요청: 없음 (유저 티어 정보는 서버에서 조회)
        - 응답: `200 OK` / `409 CONFLICT` (이미 대기 중)
    - `DELETE /api/v1/match/leave`: 매칭 대기열 취소
        - 인증 필요 (JWT)
        - 응답: `200 OK` / `400 BAD_REQUEST` (대기 중 아닐 때)

- [ ] **`MatchFacade` 또는 `MatchController` 내에서 티어 조회 처리**
    - 현재 인증 유저의 `UserRankInfo`에서 `tierScore` 계산
    - `Rank.getTierScore()` 공식 사용 (`smite-core` 참조)

### 5. 테스트

- [x] **`MatchServiceTest`** (단위 테스트, Mock 사용)
    - 정상 진입: `MATCHING` 상태 설정 + 대기열 추가 확인
    - 중복 진입: 이미 `MATCHING` 상태면 예외 확인
    - 정상 취소: 상태 초기화 + 대기열 제거 확인
    - 비대기 취소: `MATCHING`이 아닌 상태에서 취소 시 예외 확인

- [x] **`RedisMatchUserStatusStoreTest`** (통합 테스트, Testcontainers)
    - 상태 저장/조회/삭제 정합성 확인
    - TTL 만료 후 상태가 사라지는지 확인

---

## 📝 Note

### 유저 티어 조회 흐름

```
MatchController (인증 유저 ID 추출)
    ↓
UserRankInfo 조회 (smite-core/UserRankInfoRepository)
    ↓
Rank.getTierScore() 계산 → tierScore (1~28)
    ↓
MatchService.joinQueue(userId, tierScore)
```

### MatchUserStatusStore를 별도 인터페이스로 분리하는 이유
- `MatchStore`(대기열 조작)와 `MatchUserStatusStore`(유저 상태 조작)는 역할이 다름
- 향후 `MatchEngine`이 상태를 읽을 때도 동일한 인터페이스를 재사용 가능

### 다음 이슈로 이관
- `MatchEngine` 구현 (백그라운드 스케줄러, 슬라이딩 윈도우 알고리즘)
- `MatchFoundEvent` 발행

---

## 📌 Related Issue
- Closes #24
