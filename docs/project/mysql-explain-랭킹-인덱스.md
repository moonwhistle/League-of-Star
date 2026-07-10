# 랭킹 조회 API의 N+1 제거와 MySQL 인덱스 검증 계획

## 요약

랭킹 조회 API는 랭킹 목록과 유저 닉네임을 함께 제공해야 한다. 이때 랭킹 row마다 유저를 단건 조회하면 애플리케이션 레벨 N+1이 발생할 수 있으므로, rank page 조회 후 userId 목록을 모아 `users where id in (...)` 방식으로 batch 조회하도록 구성했다.

추가로 랭킹 정렬 정책이 복합 조건을 사용하므로, MySQL `EXPLAIN ANALYZE`를 통해 기존 인덱스와 개선 인덱스의 실행 계획을 비교한다.

## 단계별 개선 전략

인덱스 개선과 N+1 제거는 서로 다른 층위의 문제다.

```text
N+1 제거: SQL 실행 횟수와 DB round-trip 수를 줄이는 개선
인덱스 적용: 랭킹 목록을 가져오는 단일 SQL의 실행 계획을 개선
```

따라서 포트폴리오와 실험 설계에서는 두 효과를 섞지 않고 분리해서 측정하는 것이 좋다. 가장 깔끔한 순서는 다음과 같다.

| 단계 | N+1 상태 | 랭킹 정렬 인덱스 | 측정 목적 |
| --- | --- | --- | --- |
| 개선 전 | row별 유저 단건 조회 | 기존 인덱스만 사용 | 최악 기준선 확보 |
| 1차 개선 | batch 조회로 N+1 제거 | 기존 인덱스만 사용 | SQL 실행 횟수 감소 효과 확인 |
| 2차 개선 | batch 조회 유지 | 복합 정렬 인덱스 추가 | 단일 랭킹 쿼리 실행 계획 개선 확인 |

사용자가 처음 제안한 `개선 전 -> 인덱스 적용 -> N+1 제거` 순서도 실험은 가능하다. 다만 N+1이 남아 있는 상태에서는 전체 응답 시간이 유저 단건 조회 N회에 영향을 크게 받으므로, 인덱스 개선 효과가 응답 시간 수치에 묻힐 수 있다.

따라서 최종 문서에는 다음 흐름으로 정리한다.

```text
개선 전: 기존 인덱스 + row별 user 조회
1차 개선: 기존 인덱스 + users IN batch 조회
2차 개선: 복합 정렬 인덱스 + users IN batch 조회
```

이 순서의 장점은 다음과 같다.

- 1차 개선에서 `1 + N` 쿼리가 `1 + 1`로 줄어드는 효과를 명확히 보여줄 수 있다.
- 2차 개선에서 쿼리 수는 동일하게 유지한 채, `EXPLAIN ANALYZE`로 랭킹 SQL 자체의 실행 계획만 비교할 수 있다.
- 면접에서 “N+1과 인덱스는 각각 어떤 병목을 해결했는가?”라는 질문에 분리해서 답할 수 있다.

## 단계별 측정 항목

### 개선 전

조건:

```text
랭킹 정렬 인덱스: idx_rank_order 없음
유저 닉네임 조회: row마다 UserReadService.findById() 호출
데이터 규모: 15,000명 / 100,000명
랭킹 limit: 50
```

측정 항목:

| 항목 | 측정 방법 |
| --- | --- |
| SQL 실행 횟수 | Hibernate Statistics `prepareStatementCount` |
| API 응답 시간 | 랭킹 API p50 / p95 |
| Top 랭킹 쿼리 실행 계획 | `EXPLAIN ANALYZE` |
| 정렬 비용 | `Using filesort`, actual time 확인 |
| DB 부하 | 가능하면 MySQL slow query log 또는 performance schema 확인 |

기대되는 문제:

```text
랭킹 목록 조회 1회 + 유저 단건 조회 N회
limit=50 기준 최소 51회 SQL 실행
정렬 조건 전체를 커버하지 못해 filesort 가능성 존재
```

### 1차 개선

조건:

```text
랭킹 정렬 인덱스: idx_rank_order 없음
유저 닉네임 조회: userId 수집 후 UserReadService.findByIds() batch 조회
데이터 규모: 개선 전과 동일
랭킹 limit: 50
```

수정 방식:

```text
1. rank page 조회
2. rank rows에서 userId 목록 추출
3. users where id in (...) 조회
4. userId -> User map 생성
5. response 조립
```

측정 항목:

| 항목 | 측정 방법 |
| --- | --- |
| SQL 실행 횟수 | Hibernate Statistics |
| API 응답 시간 | 개선 전과 동일 조건으로 p50 / p95 비교 |
| Top 랭킹 쿼리 실행 계획 | 기존 인덱스 상태에서 `EXPLAIN ANALYZE` 유지 측정 |

기대 결과:

```text
limit=50 기준 SQL 실행 횟수 51회 -> 2회
DB round-trip 감소
Top 랭킹 쿼리의 filesort 가능성은 아직 남음
```

### 2차 개선

조건:

```text
랭킹 정렬 인덱스: idx_rank_order 추가
유저 닉네임 조회: users IN batch 조회 유지
데이터 규모: 개선 전/1차 개선과 동일
랭킹 limit: 50
```

수정 방식:

```sql
CREATE INDEX idx_rank_order
ON user_rank_info (
  tier_score DESC,
  lp DESC,
  total_wins DESC,
  total_losses ASC,
  total_draws DESC,
  user_id ASC
);
```

측정 항목:

| 항목 | 측정 방법 |
| --- | --- |
| SQL 실행 횟수 | 1차 개선과 동일하게 2회 유지 확인 |
| Top 랭킹 쿼리 실행 계획 | `EXPLAIN ANALYZE` before / after 비교 |
| 인덱스 사용 여부 | `key = idx_rank_order` 확인 |
| 정렬 비용 | `Using filesort` 제거 또는 감소 여부 확인 |
| 실제 실행 시간 | `actual time` 비교 |

기대 결과:

```text
SQL 실행 횟수는 2회로 유지
Top 랭킹 조회에서 정렬 정책과 인덱스 순서 일치
filesort 감소 또는 제거
데이터가 많을수록 actual time 차이가 커질 가능성
```

## 측정 시 주의점

15,000명 데이터는 매칭 부하 테스트 기준과 맞기 때문에 기본 기준으로 사용한다. 다만 MySQL 인덱스 효과를 보여주기에는 데이터가 작을 수 있다. 따라서 포트폴리오 수치를 만들 때는 `15,000명`과 함께 `100,000명` 이상 데이터를 추가로 측정하는 것이 좋다.

측정 시에는 다음 조건을 고정한다.

```text
동일한 데이터 건수
동일한 LIMIT
동일한 MySQL 서버 설정
동일한 API 인스턴스 수
동일한 JVM warm-up 이후 측정
```

API 응답 시간은 네트워크나 JVM 상태에 영향을 받을 수 있으므로, 인덱스 효과를 설명할 때는 `EXPLAIN ANALYZE` 결과를 핵심 근거로 사용한다. 반대로 N+1 제거 효과는 `Hibernate Statistics`의 SQL 실행 횟수와 API p95 응답 시간을 함께 사용한다.

## 문제 원인

애플리케이션 레벨 N+1 : `UserRankInfo`는 `User`를 JPA 연관관계로 참조하지 않고 `userId`만 보유한다. 따라서 랭킹 row마다 `UserReadService.findById()`를 호출하면 랭킹 목록 1회 조회 이후 유저 단건 조회가 N회 추가된다.

정렬 조건 대비 부족한 인덱스 : 랭킹 목록은 `tier_score`, `lp`, `total_wins`, `total_losses`, `total_draws`, `user_id` 순서로 정렬된다. 하지만 기존 DDL에는 `(tier_score, lp)` 중심 인덱스만 존재하므로, 데이터가 증가하면 정렬 과정에서 `Using filesort`가 발생할 수 있다.

실제 성장 데이터 기준 검증 필요 : 매칭 부하 테스트에서는 `50 TPS / 5분` 기준 총 `15,000명`의 매칭 진입 요청을 처리했다. 이 수치를 기본 데이터 규모로 삼되, 랭킹 테이블 성장 상황을 고려해 `100,000명` 이상의 데이터에서도 실행 계획을 비교할 필요가 있다.

## 현재 구현

랭킹 조회 흐름은 다음과 같다.

```text
1. RankReadService.findTopRankings(limit)로 랭킹 page 조회
2. rank rows에서 userId 목록 추출
3. UserReadService.findByIds(userIds)로 users batch 조회
4. userId -> User map 생성
5. ranking response 조립
```

쿼리 수 검증은 `UserRankInfoRepositoryTest`에서 Hibernate Statistics를 통해 확인한다.

```text
row별 단건 조회: rank page 1 query + users N query = 1 + N
batch 조회: rank page 1 query + users IN query 1회 = 1 + 1
```

## EXPLAIN 검증 대상

### 1. Top 랭킹 조회

현재 repository 정렬 정책은 다음과 같다.

```sql
SELECT *
FROM user_rank_info
ORDER BY tier_score DESC,
         lp DESC,
         total_wins DESC,
         total_losses ASC,
         total_draws DESC,
         user_id ASC
LIMIT 50;
```

기존 인덱스는 다음과 같다.

```sql
INDEX idx_tier_score_lp (tier_score, lp)
```

개선 후보 인덱스는 실제 정렬 정책과 동일한 복합 인덱스다.

```sql
CREATE INDEX idx_rank_order
ON user_rank_info (
  tier_score DESC,
  lp DESC,
  total_wins DESC,
  total_losses ASC,
  total_draws DESC,
  user_id ASC
);
```

### 2. 유저 전적 최신순 조회

현재 repository 조회는 다음과 같다.

```java
findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
```

기존 인덱스는 다음과 같다.

```sql
INDEX idx_user_id_created (user_id, created_at DESC)
```

개선 후보는 정렬 조건의 마지막 tie-breaker인 `id DESC`까지 포함한 인덱스다.

```sql
CREATE INDEX idx_game_records_user_created_id
ON game_records (user_id, created_at DESC, id DESC);
```

### 3. 진행 중 게임 참여 여부 조회

현재 조회는 `game_participants.user_id`로 참여 정보를 찾고, `game_rooms.status`를 확인하는 흐름이다. 기존 unique key는 `(game_room_id, user_id)` 순서라 `user_id` 선행 조회에 최적화되어 있지 않다.

개선 후보는 다음과 같다.

```sql
CREATE INDEX idx_game_participants_user_room
ON game_participants (user_id, game_room_id);
```

## 검증 절차

### 1. 기존 인덱스 기준 측정

개선 인덱스가 없는 상태에서 먼저 실행 계획을 확인한다.

```sql
SHOW INDEX FROM user_rank_info;

DROP INDEX idx_rank_order ON user_rank_info;

EXPLAIN ANALYZE
SELECT *
FROM user_rank_info
ORDER BY tier_score DESC,
         lp DESC,
         total_wins DESC,
         total_losses ASC,
         total_draws DESC,
         user_id ASC
LIMIT 50;
```

`idx_rank_order`가 아직 없다면 `DROP INDEX`는 생략한다.

### 2. 개선 인덱스 추가 후 재측정

같은 데이터, 같은 쿼리, 같은 `LIMIT` 조건으로 다시 측정한다.

```sql
CREATE INDEX idx_rank_order
ON user_rank_info (
  tier_score DESC,
  lp DESC,
  total_wins DESC,
  total_losses ASC,
  total_draws DESC,
  user_id ASC
);

EXPLAIN ANALYZE
SELECT *
FROM user_rank_info
ORDER BY tier_score DESC,
         lp DESC,
         total_wins DESC,
         total_losses ASC,
         total_draws DESC,
         user_id ASC
LIMIT 50;
```

### 3. 비교 지표

다음 항목을 before / after로 비교한다.

| 항목 | 확인 이유 |
| --- | --- |
| `key` | 어떤 인덱스를 사용하는지 확인 |
| `rows` | 예상 스캔 row 수 확인 |
| `Extra` | `Using filesort`, `Using temporary` 여부 확인 |
| `actual time` | 실제 실행 시간 비교 |
| `loops` | 반복 실행 여부 확인 |
| `cost` | 옵티마이저 비용 비교 |

## 데이터 규모

기본 기준은 매칭 부하 테스트와 맞춰 `15,000명`으로 둔다.

다만 15,000건은 MySQL에서 full scan과 filesort도 빠르게 끝날 수 있어 인덱스 효과가 작게 보일 수 있다. 따라서 랭킹 테이블 성장 상황을 가정해 다음 규모도 함께 측정한다.

```text
15,000명: 실제 매칭 부하 테스트 기준
100,000명: 랭킹 테이블 성장 상황 가정
300,000명 이상: 인덱스 효과 확인용
```

포트폴리오에는 다음처럼 표현한다.

```text
매칭 부하 테스트 기준인 15,000명 데이터를 기본으로 검증하고,
랭킹 테이블 성장 상황을 가정해 100,000명 데이터에서도 EXPLAIN ANALYZE를 비교했다.
```

## 기대 결과

기존 `(tier_score, lp)` 인덱스만 사용할 경우, 전체 정렬 조건을 모두 만족하지 못해 추가 정렬 비용이 발생할 수 있다.

개선 인덱스 `idx_rank_order`를 적용하면 Top-N 랭킹 조회에서 정렬 정책과 인덱스 순서가 일치하므로, `Using filesort`를 줄이고 상위 랭킹 row를 더 적은 비용으로 가져오는 것을 기대한다.

## 포트폴리오 요약

랭킹 조회 API에서 row별 유저 단건 조회로 발생할 수 있는 애플리케이션 레벨 N+1을 확인하고, rank page 조회 후 userId를 수집해 `users IN query`로 batch 조회하도록 개선했다. Hibernate Statistics 기반 테스트로 쿼리 수가 `1 + N`에서 `1 + 1`로 고정되는 것을 검증했다.

또한 랭킹 정렬 정책이 `tierScore`, `LP`, 승/패/무, `userId`까지 포함하는 복합 정렬이므로, 기존 `(tier_score, lp)` 인덱스와 정렬 정책 기반 복합 인덱스를 `EXPLAIN ANALYZE`로 비교해 `Using filesort`, 스캔 row 수, 실제 실행 시간을 검증하는 계획을 수립했다.
