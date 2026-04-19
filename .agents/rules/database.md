---
trigger: model_decision
description: DB, DDL, 엔티티, 테이블, 마이그레이션, 스키마 관련 작업 시 적용
---

# Database Convention (MySQL / JPA)

> **활성화**: Model Decision — "DB, DDL, 엔티티, 테이블, 마이그레이션, 스키마 관련 작업 시 적용"

---

## 필수 참조 문서

DDL/엔티티 변경 시 반드시 아래 문서와 정합성을 확인할 것:

- 비즈니스 정책: @docs/project/policy.md
- 현재 DDL 설계: @docs/DB/DDL.md

---

## 1. 삭제 전략

### Soft Delete (users 테이블)

- `users` 테이블은 **물리 삭제(DELETE) 금지.**
- `status` 컬럼으로 상태 관리: `ACTIVE` → `WITHDRAWN` → `ANONYMIZED`
- 탈퇴 시 `status = 'WITHDRAWN'`, 7일 유예 후 개인정보 마스킹 + `status = 'ANONYMIZED'`

### FK ON DELETE 정책

| 테이블 유형 | ON DELETE | 이유 |
|------------|-----------|------|
| `social_accounts`, `user_rank_info`, `promotion_series` | **CASCADE** | 유저 종속 데이터 — 유저 삭제 시 함께 정리 |
| `game_rooms`, `game_actions`, `game_records` | **RESTRICT** | 게임 이력 보존 — 실수로 삭제 방지 |

- FK에 ON DELETE 정책을 **반드시 명시**한다 (기본값 RESTRICT에 의존하지 않고 의도를 명확히).

---

## 2. 컬럼 규칙

### 공통 컬럼

- **모든 테이블**에 `created_at`, `updated_at` 포함.
- JPA에서는 `@MappedSuperclass`로 공통화.

### 타입 규칙

| 용도 | 타입 | 비고 |
|------|------|------|
| PK | `BIGINT AUTO_INCREMENT` | |
| ENUM성 문자열 | `VARCHAR(20)` | `@Enumerated(EnumType.STRING)` |
| 시간 (일반) | `DATETIME` | |
| 시간 (ms 정밀도) | `DATETIME(3)` | 게임 판정처럼 ms 단위 필요 시 |
| JSON 데이터 | `JSON` | 시나리오 데이터 등 |
| boolean | `BOOLEAN` | MySQL에서 TINYINT(1) 매핑 |

---

## 3. 테이블 가변/불변 규칙

| 유형 | 테이블 | UPDATE 허용 |
|------|--------|------------|
| **Mutable** | `users`, `user_rank_info` | ✅ |
| **진행 중 Mutable → 완료 후 Immutable** | `promotion_series`, `game_rooms` | ✅ 진행 중에만 |
| **Immutable** | `game_actions`, `game_records` | ❌ INSERT만 |

- Immutable 테이블에 UPDATE 로직이 들어가면 **즉시 리뷰** 대상.

---

## 4. DDL 변경 시 체크리스트

DDL에 변경이 생기면 반드시:

1. ✅ `DDL.md` 컬럼 스펙 테이블 갱신
2. ✅ `DDL.md` SQL CREATE TABLE 구문 갱신
3. ✅ `DDL.md` 객체 생명주기 섹션 갱신
4. ✅ `DDL.md` ER 다이어그램 갱신 (PK/FK 변경 시)
5. ✅ `DDL.md` 테이블 요약 섹션 갱신
6. ✅ `DDL.md` 변경 이력에 날짜 + 변경 내용 기록
7. ✅ `policy.md`와 정합성 교차 확인

---

## 5. 인덱스 규칙

- 인덱스는 **실제 쿼리 패턴** 기반으로 설계.
- 복합 인덱스 순서: 카디널리티 고려 + WHERE 절 순서 일치.
- 인덱스 추가 시 DDL.md에 **용도 주석** 필수.
