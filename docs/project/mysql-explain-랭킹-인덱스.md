# 랭킹 조회 API의 N+1 제거와 MySQL 인덱스 최적화

## 1. 목적

랭킹 API는 상위 랭커 목록과 각 유저의 닉네임을 함께 내려준다.

```text
user_rank_info: 티어, LP, 승/패/무, 랭킹 정렬 정보
users: 닉네임
```

따라서 랭킹 row를 조회한 뒤 각 row의 `userId`로 닉네임을 조립해야 한다. 이 과정에서 row별 단건 조회가 반복되면 N+1이 발생할 수 있고, 랭킹 정렬 쿼리 자체도 데이터가 많아지면 정렬 비용이 커질 수 있다.

이번 트러블슈팅의 목표는 다음 두 가지를 분리해서 검증하는 것이다.

```text
1. N+1 제거: SQL 실행 횟수와 DB round-trip 감소
2. 인덱스 최적화: Top 랭킹 정렬 SQL의 실행 계획 개선
```

## 2. 진행 순서

처음부터 특정 기술을 적용하지 않고, 증상과 원인을 분리해서 확인한다.

```text
1. 랭킹 API 응답 시간 측정
2. 실제 실행 SQL과 쿼리 수 확인
3. N+1 유형 판별
4. 애플리케이션 레벨 N+1 제거
5. Top 랭킹 SQL의 EXPLAIN ANALYZE 확인
6. 랭킹 정렬 복합 인덱스 적용
7. 동일 조건으로 재측정
```

이 순서로 진행하면 N+1 제거 효과와 인덱스 개선 효과를 분리해서 설명할 수 있다.

## 3. N+1 유형 판별

N+1이라고 해서 바로 fetch join이나 EntityGraph를 적용하지 않는다. 먼저 이 문제가 JPA 연관관계 lazy loading 때문에 발생하는지, 애플리케이션 코드의 반복 조회 때문에 발생하는지 구분해야 한다.

### 3.1 테이블 관계 확인

랭킹 API 응답은 `user_rank_info`의 랭킹 정보와 `users`의 닉네임을 함께 사용한다.

```text
user_rank_info.user_id -> users.id
```

`user_rank_info.user_id`에는 unique 제약이 있으므로, 두 테이블은 논리적으로 1:1 관계다.

```sql
UNIQUE KEY uk_user_id (user_id)
```

즉 랭킹 row 하나는 특정 유저 한 명의 랭킹 정보이며, 화면에 필요한 닉네임은 `users` 테이블에서 가져와야 한다.

### 3.2 JPA 엔티티 연관관계 확인

DB에 논리적 관계가 있다고 해서 JPA 엔티티 연관관계가 있는 것은 아니다. 현재 `UserRankInfo`는 `User`를 객체 참조로 들고 있지 않고, `userId` 스칼라 값만 보유한다.

```java
public class UserRankInfo {
    private Long userId;
}
```

즉 다음과 같은 JPA 연관관계가 없다.

```java
@OneToOne(fetch = FetchType.LAZY)
private User user;
```

### 3.3 Lazy Loading N+1 가능성 배제

JPA lazy loading N+1은 보통 다음과 같은 접근에서 발생한다.

```java
rankInfo.getUser().getNickname();
```

하지만 현재 모델에서는 `UserRankInfo`가 `User` 연관관계를 갖지 않으므로 위와 같은 접근 자체가 불가능하다. 따라서 이번 문제는 Hibernate가 연관관계를 lazy loading하면서 자동으로 추가 쿼리를 발생시키는 유형이 아니다.

이 때문에 fetch join이나 EntityGraph는 직접적인 해결책이 아니다. fetch join은 JPQL에서 연관관계를 함께 로딩하는 방식인데, 현재는 join fetch할 `rankInfo.user` 연관관계가 존재하지 않는다.

### 3.4 애플리케이션 레벨 N+1 판단

실제 위험 지점은 랭킹 row를 순회하면서 유저를 명시적으로 단건 조회하는 코드다.

```java
for (UserRankInfo rank : ranks) {
    User user = userReadService.findById(rank.getUserId());
}
```

이 코드는 Hibernate가 lazy loading으로 자동 쿼리를 발생시키는 것이 아니라, 애플리케이션 코드가 반복문 안에서 `findById()`를 직접 호출하는 구조다.

따라서 `limit=50`이면 유저 단건 조회가 50회 추가된다.

```text
랭킹 목록 조회 1회
+ user 단건 조회 N회
= 1 + N
```

이 기준으로 이번 문제는 **JPA 연관관계 N+1이 아니라 애플리케이션 레벨 N+1**로 분류한다.

### 3.5 Hibernate Statistics 검증

유형 판별이 맞는지 확인하기 위해 Hibernate Statistics의 `prepareStatementCount`를 사용한다.

이미 repository 테스트에서 두 흐름을 비교한다.

```text
row별 단건 조회: rank page 1 query + users N query = 1 + N
batch 조회: rank page 1 query + users IN query 1회 = 1 + 1
```

검증 방식:

```java
Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
statistics.setStatisticsEnabled(true);
statistics.clear();

// ranking query + user lookup

statistics.getPrepareStatementCount();
```

이 검증을 통해 row별 단건 조회 baseline에서는 user 조회가 row 수만큼 증가하고, userId를 모아 `findByIds()`로 batch 조회하면 user 조회가 IN query 1회로 고정되는지 확인한다.

## 4. 개선 방식

### 개선 전 Baseline

측정용 baseline은 운영 로직을 망가뜨리지 않기 위해 별도 서비스로 분리했다.

```text
RankingBaselineService
- 트러블슈팅 측정 전용
- 랭킹 row마다 userReadService.findById() 호출
- 의도적으로 애플리케이션 레벨 N+1 재현
```

측정 endpoint:

```text
GET /api/v1/rankings/baseline?limit=50
```

### 1차 개선: Batch 조회

N+1 유형이 애플리케이션 레벨 반복 조회이므로 fetch join이 아니라 batch 조회를 선택한다.

```text
1. rank page 조회
2. rank rows에서 userId 목록 추출
3. users where id in (...) 조회
4. userId -> User map 생성
5. ranking response 조립
```

측정 endpoint:

```text
GET /api/v1/rankings?limit=50
```

쿼리 수 기대값:

```text
닉네임 조립 구간
- 개선 전: users 단건 조회 N회
- 1차 개선 후: users IN 조회 1회

API 전체 기준
- 개선 전 baseline: 약 55회
- 1차 개선 후: 약 6회
```

### 2차 개선: 랭킹 정렬 인덱스

N+1을 제거해도 Top 랭킹 목록을 가져오는 SQL 자체가 비효율적이면 응답 시간이 남을 수 있다. 따라서 랭킹 정렬 쿼리의 실행 계획을 `EXPLAIN ANALYZE`로 확인한다.

랭킹 API의 조회 흐름은 다음과 같다.

```text
1. user_rank_info에서 상위 랭킹 row를 limit만큼 조회
2. 조회된 rank row의 userId를 수집
3. users 테이블에서 닉네임을 batch 조회
4. rank row와 user nickname을 조립해 응답 생성
```

1차 개선으로 2~3번의 N+1은 제거했다. 하지만 1번의 Top 랭킹 조회는 여전히 `user_rank_info` 테이블의 정렬 성능에 의존한다.

분석 대상 쿼리:

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

랭킹 정렬 정책은 다음 순서로 고정되어 있다.

| 정렬 컬럼 | 방향 | 이유 |
| --- | --- | --- |
| `tier_score` | DESC | 높은 티어 유저가 먼저 노출되어야 함 |
| `lp` | DESC | 같은 티어에서는 LP가 높은 유저가 먼저 노출되어야 함 |
| `total_wins` | DESC | 같은 티어/LP에서는 승리 수가 많은 유저 우선 |
| `total_losses` | ASC | 패배 수가 적은 유저 우선 |
| `total_draws` | DESC | 나머지 조건이 같을 때 무승부 수를 tie-breaker로 사용 |
| `user_id` | ASC | 모든 조건이 같을 때 결과 순서를 안정적으로 고정 |

`user_rank_info` 테이블은 유저별 현재 랭크 상태를 1 row로 보관한다.

```text
users 1명당 user_rank_info 1 row
랭킹 조회는 전체 랭커 중 상위 N명을 조회
WHERE 조건 없이 ORDER BY + LIMIT 구조로 실행
데이터가 늘수록 정렬 대상 row 수가 증가
```

따라서 `PRIMARY`나 `user_id` unique index만으로는 랭킹 정렬을 해결할 수 없다. `LIMIT 50`이 있어도 정렬 조건을 만족하는 인덱스가 없으면 DB는 랭킹 순서를 만들기 위해 Sort를 수행해야 한다.

개선 후보 인덱스:

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

이 인덱스의 목적은 DB가 별도 정렬을 수행하지 않고, 인덱스 순서대로 상위 50개 row를 읽도록 유도하는 것이다.

컬럼 순서를 정렬 정책과 동일하게 둔 이유는 다음과 같다.

```text
ORDER BY와 인덱스 컬럼 순서가 일치해야 MySQL이 인덱스 순서를 정렬 결과로 활용할 수 있다.
Top-N 조회에서는 인덱스 순서대로 앞에서부터 읽고 LIMIT 50에서 멈출 수 있는 구조가 유리하다.
마지막에 user_id를 포함해 동률 상황에서도 결과 순서를 안정적으로 고정한다.
```

즉 2차 개선은 N+1과는 다른 문제를 해결한다.

```text
1차 개선: SQL 실행 횟수 감소
2차 개선: 남아 있는 Top 랭킹 SQL의 정렬 비용 감소
```

## 5. 측정 조건

현재 측정 기준:

```text
데이터 규모: users 100,000건 / user_rank_info 100,000건
limit: 50
개선 전 인덱스: PRIMARY, uk_user_id
랭킹 정렬 인덱스: 없음
```

인덱스 상태 기준:

```text
개선 전
- PRIMARY
- uk_user_id

2차 개선 후
- PRIMARY
- uk_user_id
- idx_rank_order
```

측정 항목:

| 항목 | 측정 방법 | 목적 |
| --- | --- | --- |
| API 응답 시간 | `docs/load-test/ranking-api-repeat.js` 단일 VU 반복 호출 | E2E 응답 시간 비교 |
| SQL 실행 횟수 | Hibernate Statistics `prepareStatementCount` | N+1 제거 효과 확인 |
| 실제 실행 SQL | Hibernate SQL log 또는 MySQL general log | EXPLAIN 대상 쿼리와 실제 쿼리 일치 확인 |
| 실행 계획 | `EXPLAIN ANALYZE` | Sort, scan, actual time 확인 |

## 6. 단계별 비교표

최종 결과는 아래 표를 채우는 방식으로 정리한다.

| 단계 | 코드 상태 | 인덱스 상태 | 쿼리 수 | API p95 | 실행 계획 | Top 랭킹 SQL 시간 |
| --- | --- | --- | ---: | ---: | --- | ---: |
| 개선 전 | row별 `findById()` | `PRIMARY`, `uk_user_id` | 약 55회 | 139.97ms | Sort + Limit | 약 31.6ms |
| 1차 개선 | `findByIds()` batch 조회 | `PRIMARY`, `uk_user_id` | 약 6회 | 82.67ms | Sort + Limit | 동일 조건 |
| 2차 개선 | `findByIds()` batch 조회 | `idx_rank_order` 추가 | 약 6회 | 49.61ms | Index Scan | 약 0.201ms |

## 7. 개선 전 Baseline API 응답 시간

개선 전 baseline endpoint를 단일 VU로 30회 반복 호출했다.

```text
GET /api/v1/rankings/baseline?limit=50
```

측정 조건:

```text
환경: 로컬 API 서버 + Docker MySQL
데이터 규모: users 100,000건 / user_rank_info 100,000건
인덱스 상태: PRIMARY, uk_user_id
랭킹 정렬 인덱스: 없음
코드 상태: RankingBaselineService 기준 row별 findById() 호출
측정 도구: k6
VUS: 1
Iterations: 30
Limit: 50
실패율: 0.00%
```

반복 측정 결과:

| 지표 | 값 |
| --- | ---: |
| avg | 125.17ms |
| min | 117.94ms |
| med | 122.89ms |
| p90 | 130.78ms |
| p95 | 139.97ms |
| max | 152.36ms |
| http_req_failed | 0.00% |

아래 이미지는 같은 baseline endpoint를 단일 요청으로 호출했을 때의 참고 캡처다. 단일 요청에서는 총 응답 시간이 약 `351.97ms`로 관측됐지만, 대표 비교 지표는 30회 반복 측정 결과의 p95인 `139.97ms`를 사용한다.

![개선 전 baseline 랭킹 API 응답 시간](./images/ranking-baseline-api-time.png)

해석:

```text
1. 단일 VU 30회 반복 측정 기준 p95는 139.97ms다.
2. 단일 요청 캡처에서는 351.97ms가 관측됐지만, 1회 측정값은 대표값으로 사용하지 않는다.
3. k6 반복 측정에서 실패율은 0.00%로, baseline endpoint는 정상 응답했다.
4. 현재 값은 로컬 환경 기준이므로 EC2 등 배포 환경에서는 동일 조건으로 별도 재측정이 필요하다.
```

## 8. 개선 전 EXPLAIN ANALYZE 결과

개선 전 상태에서 Top 랭킹 쿼리를 분석했다.

```sql
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

측정 결과 일부:

```text
-> Limit: 50 row(s)  (cost=10107 rows=50) (actual time=31.6..31.6 rows=50 loops=1)
    -> Sort: user_rank_info.tier_score DESC,
             user_rank_info.lp DESC,
             user_rank_info.total_wins DESC,
             user_rank_info.total_losses,
             user_rank_info.total_draws DESC,
             user_rank_info.user_id
```

해석:

```text
1. 최종 반환 row는 50개다.
2. 하지만 랭킹 정렬 조건을 만족하는 인덱스가 없어 Sort 단계가 발생했다.
3. LIMIT 50이 있어도 인덱스 순서대로 상위 50개만 바로 읽는 구조가 아니다.
4. 100,000건 기준 최종 50개 반환까지 약 31.6ms가 소요됐다.
```

현재 확인된 핵심 문제:

| 항목 | 개선 전 결과 |
| --- | --- |
| 조회 목적 | 상위 랭킹 50명 조회 |
| 데이터 규모 | 100,000건 |
| 랭킹 정렬 인덱스 | 없음 |
| 실행 계획 핵심 | `Sort` 후 `Limit 50` |
| 반환 row 수 | 50건 |
| 실제 실행 시간 | 약 31.6ms |
| 문제점 | 정렬 조건을 만족하는 복합 인덱스가 없어 Sort 비용 발생 |

전체 `EXPLAIN ANALYZE` 결과에서 `Table scan on user_rank_info` 줄이 확인되면, 해당 내용을 추가해 스캔 row 수까지 기록한다.

## 9. 1차 개선 API 응답 시간

1차 개선 endpoint를 단일 VU로 30회 반복 호출했다.

```text
GET /api/v1/rankings?limit=50
```

측정 조건:

```text
환경: 로컬 API 서버 + Docker MySQL
데이터 규모: users 100,000건 / user_rank_info 100,000건
인덱스 상태: PRIMARY, uk_user_id
랭킹 정렬 인덱스: 없음
코드 상태: RankingService 기준 findByIds() batch 조회
측정 도구: k6
VUS: 1
Iterations: 30
Limit: 50
실패율: 0.00%
```

반복 측정 결과:

| 지표 | 값 |
| --- | ---: |
| avg | 75.14ms |
| min | 69.76ms |
| med | 74.71ms |
| p90 | 81.27ms |
| p95 | 82.67ms |
| max | 85.78ms |
| http_req_failed | 0.00% |

개선 전 baseline과 비교하면 p95가 `139.97ms`에서 `82.67ms`로 감소했다.

```text
139.97ms -> 82.67ms
감소폭: 57.30ms
개선율: 약 40.9%
```

해석:

```text
1. 인덱스 상태는 개선 전과 동일하게 PRIMARY, uk_user_id만 유지했다.
2. 따라서 이번 개선의 주요 차이는 row별 findById() 반복 호출을 findByIds() batch 조회로 바꾼 점이다.
3. Top 랭킹 SQL의 Sort 비용은 아직 남아 있지만, users 단건 조회 반복이 제거되면서 E2E 응답 시간이 감소했다.
4. Hibernate Statistics로 쿼리 수 감소를 확인한 뒤, 이후 idx_rank_order를 추가해 Top 랭킹 SQL 자체의 실행 계획을 개선했다.
```

## 10. 2차 개선: 인덱스 적용 후 실행 계획과 API 응답 시간

2차 개선에서는 `user_rank_info`의 랭킹 정렬 정책과 동일한 복합 인덱스를 추가했다.

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

### EXPLAIN ANALYZE 결과

인덱스 적용 후 동일한 Top 랭킹 쿼리를 다시 분석했다.

```sql
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

결과:

```text
-> Limit: 50 row(s)  (cost=0.0708 rows=50) (actual time=0.196..0.201 rows=50 loops=1)
    -> Index scan on user_rank_info using idx_rank_order
       (cost=0.0708 rows=50) (actual time=0.195..0.198 rows=50 loops=1)
```

개선 전후 비교:

| 항목 | 개선 전 | 2차 개선 후 |
| --- | ---: | ---: |
| 실행 계획 | Sort + Limit | Index Scan + Limit |
| 사용 인덱스 | 없음 | `idx_rank_order` |
| Top 랭킹 SQL actual time | 31.6ms | 0.201ms |
| 개선 배율 | - | 약 157배 |
| 실행 시간 감소율 | - | 약 99.36% |

해석:

```text
1. 개선 전에는 정렬 조건을 만족하는 인덱스가 없어 Sort 후 상위 50개를 반환했다.
2. 인덱스 적용 후에는 idx_rank_order를 사용한 Index Scan으로 실행 계획이 변경됐다.
3. DB가 별도 정렬을 수행하지 않고 인덱스 순서대로 50개 row를 읽고 종료했다.
4. Top 랭킹 SQL 단독 실행 시간은 약 31.6ms에서 0.201ms로 감소했다.
```

주의할 점은 이 수치가 API 전체 응답 시간이 아니라 Top 랭킹 SQL 단독 실행 시간이라는 점이다. API 전체에는 인증, 현재 유저 조회, 내 순위 계산, users batch 조회, JSON 직렬화 비용이 남아 있다.

### API 반복 측정 결과

인덱스 적용 후 optimized endpoint를 단일 VU로 30회 반복 호출했다.

```text
GET /api/v1/rankings?limit=50
```

측정 조건:

```text
환경: 로컬 API 서버 + Docker MySQL
데이터 규모: users 100,000건 / user_rank_info 100,000건
인덱스 상태: PRIMARY, uk_user_id, idx_rank_order
코드 상태: RankingService 기준 findByIds() batch 조회
측정 도구: k6
VUS: 1
Iterations: 30
Limit: 50
실패율: 0.00%
```

반복 측정 결과:

| 지표 | 값 |
| --- | ---: |
| avg | 44.02ms |
| min | 39.31ms |
| med | 43.03ms |
| p90 | 48.45ms |
| p95 | 49.61ms |
| max | 50.62ms |
| http_req_failed | 0.00% |

E2E API p95 비교:

| 단계 | API p95 |
| --- | ---: |
| 개선 전 baseline | 139.97ms |
| 1차 개선: N+1 제거 | 82.67ms |
| 2차 개선: 인덱스 추가 | 49.61ms |

개선 폭:

```text
개선 전 -> 1차 개선: 139.97ms -> 82.67ms, 약 40.9% 감소
1차 개선 -> 2차 개선: 82.67ms -> 49.61ms, 약 40.0% 감소
개선 전 -> 2차 개선: 139.97ms -> 49.61ms, 약 64.6% 감소
```

해석:

```text
1. 1차 개선으로 users 단건 조회 반복을 제거해 API p95가 139.97ms에서 82.67ms로 감소했다.
2. 2차 개선으로 Top 랭킹 SQL의 Sort 비용을 제거해 API p95가 49.61ms까지 감소했다.
3. SQL 단독 실행 시간은 약 157배 개선됐지만, API 전체 응답 시간은 다른 처리 비용도 포함하므로 p95 기준 약 64.6% 감소로 해석한다.
```

## 11. 인덱스 선택 실험: 조회 성능과 쓰기 비용 비교

랭킹 조회 성능만 보면 정렬 조건을 모두 포함한 `idx_rank_order`를 추가하는 것이 가장 유리하다. 하지만 랭킹 정산 시 `lp`, `tier_score`, `total_wins` 같은 랭킹 컬럼이 갱신되므로, 인덱스를 많이 추가하면 쓰기 성능이 저하될 수 있다.

따라서 다음 4가지 인덱스 구성을 비교했다.

```text
A: PRIMARY, uk_user_id만 유지
B: A + 랭킹 정렬 전용 인덱스(idx_rank_order)
C: A + 기존 후보 인덱스 2개(idx_tier_score_lp, idx_tier_division_lp)
D: C + 랭킹 정렬 전용 인덱스(idx_rank_order)
```

### 측정 결과

| 구성 | 인덱스 구성 | 조회 계획 | 조회 시간 | 10,000건 UPDATE |
| --- | --- | --- | ---: | ---: |
| A | 기본 인덱스만 유지 | Sort + Limit | 32.2ms | 81.855ms |
| B | `idx_rank_order` | Index Scan + Limit | 0.119ms | 226.931ms |
| C | 기존 후보 인덱스 2개 | Sort + Limit | 31.2ms | 197.142ms |
| D | 기존 후보 인덱스 2개 + `idx_rank_order` | Index Scan + Limit | 0.116ms | 393.738ms |

### 해석

```text
1. A는 인덱스 유지 비용이 가장 적어 쓰기는 가장 빠르지만, 랭킹 조회 시 Sort가 발생해 조회 성능이 낮다.
2. B는 랭킹 정렬 조건과 동일한 복합 인덱스를 사용해 Sort를 제거했고, 조회 시간이 32.2ms에서 0.119ms로 감소했다.
3. C의 기존 후보 인덱스는 랭킹 ORDER BY 전체 순서를 만족하지 못해 Sort가 그대로 발생했다.
4. D는 조회 성능은 B와 거의 동일하지만, 유지해야 할 인덱스가 많아 10,000건 UPDATE 비용이 가장 컸다.
```

B와 D의 조회 시간은 각각 0.119ms, 0.116ms로 거의 차이가 없었다. 반면 쓰기 시간은 B가 226.931ms, D가 393.738ms로 D가 약 73.5% 더 느렸다.

따라서 최종적으로는 기존 후보 인덱스를 모두 유지하지 않고, 실제 랭킹 조회 패턴에 직접 대응하는 `idx_rank_order`만 선택하는 것이 가장 합리적이라고 판단했다.

포트폴리오 정리 문장:

```text
랭킹 조회는 ORDER BY + LIMIT 구조로 동작하므로 정렬 정책과 동일한 복합 인덱스를 설계했다.
단순히 인덱스를 많이 추가하면 조회 성능은 유지되더라도 랭크 정산 시 쓰기 비용이 증가할 수 있어,
A/B/C/D 실험으로 조회 성능과 UPDATE 비용을 함께 비교했다.
그 결과 B안은 조회 시간을 32.2ms에서 0.119ms로 줄이면서도 D안 대비 쓰기 비용을 약 42.4% 낮게 유지해 최종 인덱스로 선택했다.
```

## 12. SQL 실행 횟수 검증

Hibernate Statistics로 baseline과 개선 후 흐름의 SQL 실행 횟수를 비교했다.

| 구분 | 닉네임 조립 방식 | SQL 실행 횟수 | 해석 |
| --- | --- | ---: | --- |
| 개선 전 baseline | 랭킹 row마다 `findById()` 단건 조회 | 약 55회 | limit=50 기준 users 단건 조회가 row 수만큼 반복됨 |
| 1차 개선 후 | userId 수집 후 `findByIds()` batch 조회 | 약 6회 | users 조회가 IN query 1회로 고정됨 |
| 2차 개선 후 | 1차 개선과 동일 | 약 6회 | 인덱스는 쿼리 수가 아니라 Top 랭킹 SQL의 Sort 비용을 줄임 |

이 결과로 이번 N+1은 JPA 연관관계 lazy loading이 아니라, 반복문 안에서 user 단건 조회를 직접 호출한 애플리케이션 레벨 N+1임을 확인했다.

API 응답 시간은 다음 스크립트로 측정한다.

```bash
MODE=baseline API_BASE_URL=http://localhost:8080 TOKEN=<access-token> ITERATIONS=30 k6 run docs/load-test/ranking-api-repeat.js
MODE=optimized API_BASE_URL=http://localhost:8080 TOKEN=<access-token> ITERATIONS=30 k6 run docs/load-test/ranking-api-repeat.js
```

이 측정은 부하테스트가 아니라 단일 VU 반복 측정이다. 결과에서는 `http_req_duration`의 `avg`, `med`, `p(90)`, `p(95)`를 기록한다.

최종적으로 다음 문장 형태로 정리한다.

```text
랭킹 조회 API에서 닉네임 조립 방식에 따라 애플리케이션 레벨 N+1이 발생할 수 있음을 확인했다.
측정용 baseline을 구성해 row별 user 단건 조회를 재현했고,
Hibernate Statistics로 SQL 실행 횟수가 약 55회에서 약 6회로 감소함을 확인했다.

1차 개선으로 userId를 수집해 users IN query로 batch 조회하도록 변경했고,
2차 개선으로 랭킹 정렬 정책과 동일한 복합 인덱스를 추가해 Top 랭킹 SQL의 Sort 비용을 줄였다.
```
