# League of Smite — Database Design (DDL)

---

## 1. 전체 테이블 구조도

```mermaid
erDiagram
    users ||--|| user_rank_info : has
    users ||--o{ social_accounts : links
    users ||--o{ promotion_series : enters
    users ||--o{ game_records : records
    users ||--o{ game_actions : performs
    users ||--o{ game_rooms : plays
    game_rooms ||--o{ game_actions : contains
    game_rooms ||--o{ game_records : generates

    users {
        BIGINT id PK
    }
    social_accounts {
        BIGINT id PK
        BIGINT user_id FK
    }
    user_rank_info {
        BIGINT id PK
        BIGINT user_id FK
    }
    promotion_series {
        BIGINT id PK
        BIGINT user_id FK
    }
    game_rooms {
        BIGINT id PK
        BIGINT player1_id FK
        BIGINT player2_id FK
        BIGINT winner_id FK
    }
    game_actions {
        BIGINT id PK
        BIGINT game_room_id FK
        BIGINT user_id FK
    }
    game_records {
        BIGINT id PK
        BIGINT game_room_id FK
        BIGINT user_id FK
        BIGINT opponent_id FK
    }
```

> 컬럼 상세는 3 참고. ER 다이어그램은 관계 파악을 위해 PK/FK만 표시합니다.

---

## 2. 객체 생명주기

### 2.1 users

```mermaid
stateDiagram-v2
    [*] --> Active : 회원가입
    Active --> Active : 이메일 인증 / 닉네임·비밀번호 변경
    Active --> Withdrawn : 탈퇴 요청
    Withdrawn --> Active : 유예 기간 내 취소
    Withdrawn --> Anonymized : 7일 유예 후 익명화
    Anonymized --> [*]
```

> **Soft Delete 전략**: 유저 행은 물리적으로 삭제하지 않습니다. 탈퇴 시 `status=WITHDRAWN`으로 마킹하고, 7일 유예 후 개인정보(email, nickname, password)를 마스킹하여 `status=ANONYMIZED`로 전환합니다. 전적 기록(game_records, game_rooms)의 FK 참조가 유지되므로 전적 보존과 익명화가 동시에 달성됩니다.

| 시점 | 동작 | 비고 |
|------|------|------|
| 회원가입 | INSERT | status=ACTIVE, email_verified=FALSE |
| 이메일 인증 | UPDATE | email_verified=TRUE |
| 닉네임/비밀번호 변경 | UPDATE | updated_at 갱신 |
| 탈퇴 요청 | UPDATE | status=WITHDRAWN, withdrawn_at=NOW() |
| 탈퇴 취소 (7일 이내) | UPDATE | status=ACTIVE, withdrawn_at=NULL |
| 7일 유예 후 익명화 | UPDATE | status=ANONYMIZED, email·nickname·password 마스킹 |

### 2.2 social_accounts

| 시점 | 동작 | 비고 |
|------|------|------|
| 소셜 로그인 최초 연동 | INSERT | 이메일 기준 users와 매핑 |
| 연동 해제 | DELETE | 비밀번호 설정 후에만 가능 |
| 유저 탈퇴 | CASCADE DELETE | users 삭제 시 함께 삭제 |

### 2.3 user_rank_info

```mermaid
stateDiagram-v2
    [*] --> Placement : 회원가입 시 생성
    Placement --> Ranked : 배치 10판 완료
    Ranked --> Ranked : LP 증감/강등/승급
    Ranked --> InPromotion : LP 99 or 100 도달
    InPromotion --> Ranked : 승급 성공 또는 실패
```

| 시점 | 동작 | 비고 |
|------|------|------|
| 회원가입 | INSERT | is_in_placement=TRUE, IRON IV, LP 0 |
| 배치 게임 결과 | UPDATE | placement_wins 또는 placement_losses 증가 |
| 배치 완료 | UPDATE | is_in_placement=FALSE, 승수 기반 티어 배정 |
| 게임 승리 | UPDATE | lp 증가, total_wins 증가 |
| 게임 패배 | UPDATE | lp 감소, total_losses 증가 |
| 게임 무승부 | UPDATE | total_draws 증가 (LP 변동 없음) |
| 승급 성공 | UPDATE | tier/division/tier_score 변경, lp=0, demotion_shield=3 |
| 승리 (보호 중) | UPDATE | demotion_shield=0 (승리 시 강등 보호 즉시 해제) |
| 강등 | UPDATE | tier/division/tier_score 변경, lp=75, demotion_shield=0 |

### 2.4 promotion_series

```mermaid
stateDiagram-v2
    [*] --> InProgress : LP 99 or 100 도달
    InProgress --> InProgress : 게임 결과 기록
    InProgress --> Promoted : 2승 달성
    InProgress --> Failed : 2승 불가능 확정
    Promoted --> [*]
    Failed --> [*]
```

| 시점 | 동작 | 비고 |
|------|------|------|
| LP 99/100 도달 | INSERT | status=IN_PROGRESS, 모든 game result=PENDING |
| 승급전 경기 결과 | UPDATE | game1/2/3_result 갱신 |
| 2승 달성 | UPDATE | status=PROMOTED, completed_at 기록 |
| 2승 불가능 확정 | UPDATE | status=FAILED, completed_at 기록 |

### 2.5 game_rooms

```mermaid
stateDiagram-v2
    [*] --> InProgress : 매칭 수락 완료
    InProgress --> Finished : 정상 종료
    InProgress --> Aborted : 디스커넥트
    Finished --> [*]
    Aborted --> [*]
```

| 시점 | 동작 | 비고 |
|------|------|------|
| 매칭 수락 | INSERT | status=IN_PROGRESS, scenario_data 생성 |
| 게임 시작 | UPDATE | game_start_time 기록 (ms 정밀도) |
| 게임 종료 | UPDATE | status=FINISHED, result/winner_id, finished_at |
| 디스커넥트 | UPDATE | status=ABORTED, 이탈자 패배/상대 승리 |

### 2.6 game_actions — Immutable

| 시점 | 동작 | 비고 |
|------|------|------|
| 강타 입력 | INSERT | 서버 수신 시각, RTT 보정, HP 역산 결과 저장 |

- **불변(Immutable)**: 한번 기록되면 수정 없음
- **조건부 생성**: 강타 사용 시에만 생성 (미사용 시 행 없음)
- **게임당 최대 2행**: UK 제약으로 중복 방지

### 2.7 game_records — Immutable

| 시점 | 동작 | 비고 |
|------|------|------|
| 게임 종료 | INSERT (2행) | 양쪽 플레이어 각 1행 동시 생성 |

- **불변(Immutable)**: 전적 기록은 수정하지 않음
- **항상 2행 생성**: player1/player2 각각의 관점에서 기록
- **LP 스냅샷**: lp_before/after, tier_before/after로 변동 이력 추적

---

## 3. 테이블 상세 설계

> 모든 테이블에 `created_at`, `updated_at` 컬럼이 포함됩니다.

### 3.1 users — 계정 정보

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 유저 고유 ID |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | 로그인 이메일 |
| `password` | VARCHAR(255) | NULLABLE | BCrypt 해시. 소셜 전용 계정은 NULL |
| `nickname` | VARCHAR(16) | UNIQUE, NOT NULL | 게임 닉네임 (2~16자) |
| `email_verified` | BOOLEAN | NOT NULL, DEFAULT FALSE | 이메일 인증 완료 여부 |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / WITHDRAWN / ANONYMIZED |
| `withdrawn_at` | DATETIME | NULLABLE | 탈퇴 요청 시각 (7일 유예 기준점, 30일 재가입 제한 기준) |
| `created_at` | DATETIME | NOT NULL | 가입일시 |
| `updated_at` | DATETIME | NOT NULL | 수정일시 |

```sql
CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL,
    password       VARCHAR(255) NULL,
    nickname       VARCHAR(16)  NOT NULL,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    withdrawn_at   DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_nickname (nickname),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

### 3.2 social_accounts — 소셜 로그인 연동

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 고유 ID |
| `user_id` | BIGINT | FK → users, NOT NULL | 연동된 유저 |
| `provider` | VARCHAR(20) | NOT NULL | GOOGLE, DISCORD |
| `provider_id` | VARCHAR(255) | NOT NULL | 소셜 플랫폼 유저 ID |
| `provider_email` | VARCHAR(255) | NULLABLE | 소셜 플랫폼 이메일 |
| `created_at` | DATETIME | NOT NULL | 연동일시 |
| `updated_at` | DATETIME | NOT NULL | 수정일시 |

```sql
CREATE TABLE social_accounts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    provider        VARCHAR(20)  NOT NULL,
    provider_id     VARCHAR(255) NOT NULL,
    provider_email  VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_provider_id (provider, provider_id),
    INDEX idx_user_id (user_id),
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

### 3.3 user_rank_info — 랭크 정보

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 고유 ID |
| `user_id` | BIGINT | FK → users, UNIQUE, NOT NULL | 유저 (1:1) |
| `tier` | VARCHAR(20) | NOT NULL | IRON ~ CHALLENGER |
| `division` | VARCHAR(5) | NULLABLE | I ~ IV (Apex는 NULL) |
| `lp` | INT | NOT NULL, DEFAULT 0 | 현재 LP |
| `tier_score` | INT | NULLABLE | 매칭용 점수 1~28 (Apex는 NULL, 비정규화) |
| `total_wins` | INT | NOT NULL, DEFAULT 0 | 총 승리 수 |
| `total_losses` | INT | NOT NULL, DEFAULT 0 | 총 패배 수 |
| `total_draws` | INT | NOT NULL, DEFAULT 0 | 총 무승부 수 |
| `is_in_placement` | BOOLEAN | NOT NULL, DEFAULT TRUE | 배치 진행 중 여부 |
| `placement_wins` | INT | NOT NULL, DEFAULT 0 | 배치 승리 수 |
| `placement_losses` | INT | NOT NULL, DEFAULT 0 | 배치 패배 수 |
| `demotion_shield` | INT | NOT NULL, DEFAULT 0 | 강등 보호 남은 횟수 (0~3) |
| `created_at` | DATETIME | NOT NULL | 생성일시 |
| `updated_at` | DATETIME | NOT NULL | 최종 갱신일시 |

```sql
CREATE TABLE user_rank_info (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    tier              VARCHAR(20) NOT NULL DEFAULT 'IRON',
    division          VARCHAR(5)  NULL     DEFAULT 'IV',
    lp                INT         NOT NULL DEFAULT 0,
    tier_score        INT         NULL     DEFAULT 1,
    total_wins        INT         NOT NULL DEFAULT 0,
    total_losses      INT         NOT NULL DEFAULT 0,
    total_draws       INT         NOT NULL DEFAULT 0,
    is_in_placement   BOOLEAN     NOT NULL DEFAULT TRUE,
    placement_wins    INT         NOT NULL DEFAULT 0,
    placement_losses  INT         NOT NULL DEFAULT 0,
    demotion_shield   INT         NOT NULL DEFAULT 0,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    INDEX idx_tier_score_lp (tier_score, lp),
    INDEX idx_tier_division_lp (tier, division, lp),
    CONSTRAINT fk_user_rank_info_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> **인덱스 설명**
> - `idx_tier_score_lp`: 매칭 시 ± 3 디비전 범위 검색용
> - `idx_tier_division_lp`: 리더보드 정렬용

---

### 3.4 promotion_series — 승급전 진행 상태

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 고유 ID |
| `user_id` | BIGINT | FK → users, NOT NULL | 승급전 진행 유저 |
| `target_tier` | VARCHAR(20) | NOT NULL | 승급 목표 티어 |
| `target_division` | VARCHAR(5) | NULLABLE | 승급 목표 디비전 (Master 승급 시 NULL) |
| `game1_result` | VARCHAR(10) | NOT NULL, DEFAULT 'PENDING' | 1판 결과 (WIN/LOSS/DRAW/PENDING) |
| `game2_result` | VARCHAR(10) | NOT NULL, DEFAULT 'PENDING' | 2판 결과 |
| `game3_result` | VARCHAR(10) | NOT NULL, DEFAULT 'PENDING' | 3판 결과 |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'IN_PROGRESS' | IN_PROGRESS / PROMOTED / FAILED |
| `completed_at` | DATETIME | NULLABLE | 승급전 완료일시 |
| `created_at` | DATETIME | NOT NULL | 승급전 시작일시 |
| `updated_at` | DATETIME | NOT NULL | 수정일시 |

```sql
CREATE TABLE promotion_series (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    target_tier      VARCHAR(20) NOT NULL,
    target_division  VARCHAR(5)  NULL,
    game1_result     VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    game2_result     VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    game3_result     VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    status           VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    completed_at     DATETIME    NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_id_status (user_id, status),
    CONSTRAINT fk_promotion_series_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

### 3.5 game_rooms — 게임 방

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 게임 고유 ID |
| `player1_id` | BIGINT | FK → users, NOT NULL | 플레이어 1 |
| `player2_id` | BIGINT | FK → users, NOT NULL | 플레이어 2 |
| `status` | VARCHAR(20) | NOT NULL | IN_PROGRESS / FINISHED / ABORTED |
| `result` | VARCHAR(20) | NULLABLE | PLAYER1_WIN / PLAYER2_WIN / DRAW |
| `winner_id` | BIGINT | FK → users, NULLABLE | 승자 (무승부 시 NULL, 쿼리 편의용 비정규화) |
| `dragon_max_hp` | INT | NOT NULL, DEFAULT 10000 | 드래곤 초기 HP |
| `duration_seconds` | INT | NOT NULL | 게임 시간 (7~15) |
| `scenario_data` | JSON | NOT NULL | HP 감소 시나리오 |
| `game_start_time` | DATETIME(3) | NULLABLE | 게임 실제 시작 시각 (ms 정밀도) |
| `finished_at` | DATETIME | NULLABLE | 게임 종료일시 |
| `created_at` | DATETIME | NOT NULL | 방 생성일시 |
| `updated_at` | DATETIME | NOT NULL | 수정일시 |

```sql
CREATE TABLE game_rooms (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    player1_id         BIGINT      NOT NULL,
    player2_id         BIGINT      NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    result             VARCHAR(20) NULL,
    winner_id          BIGINT      NULL,
    dragon_max_hp      INT         NOT NULL DEFAULT 10000,
    duration_seconds   INT         NOT NULL,
    scenario_data      JSON        NOT NULL,
    game_start_time    DATETIME(3) NULL,
    finished_at        DATETIME    NULL,
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_player1_id (player1_id),
    INDEX idx_player2_id (player2_id),
    INDEX idx_status (status),
    CONSTRAINT fk_game_rooms_player1 FOREIGN KEY (player1_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_rooms_player2 FOREIGN KEY (player2_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_rooms_winner  FOREIGN KEY (winner_id)  REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> **game_start_time이 DATETIME(3)인 이유**: ms 단위 정밀도 필요 (1ms 차이로 승패 결정)

---

### 3.6 game_actions — 강타 액션 기록

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 고유 ID |
| `game_room_id` | BIGINT | FK → game_rooms, NOT NULL | 게임 방 |
| `user_id` | BIGINT | FK → users, NOT NULL | 강타 사용 유저 |
| `server_receive_time_ms` | BIGINT | NOT NULL | 서버 수신 시각 (epoch ms) |
| `rtt_ms` | INT | NOT NULL | 측정된 RTT (ms) |
| `smite_time_ms` | INT | NOT NULL | 보정된 강타 시점 (게임 시작 기준 ms) |
| `dragon_hp_at_smite` | INT | NOT NULL | 역산된 드래곤 HP |
| `is_kill` | BOOLEAN | NOT NULL | 킬 성공 여부 (HP 1200 이하) |
| `created_at` | DATETIME | NOT NULL | 기록일시 |
| `updated_at` | DATETIME | NOT NULL | 수정일시 |

```sql
CREATE TABLE game_actions (
    id                      BIGINT   NOT NULL AUTO_INCREMENT,
    game_room_id            BIGINT   NOT NULL,
    user_id                 BIGINT   NOT NULL,
    server_receive_time_ms  BIGINT   NOT NULL,
    rtt_ms                  INT      NOT NULL,
    smite_time_ms           INT      NOT NULL,
    dragon_hp_at_smite      INT      NOT NULL,
    is_kill                 BOOLEAN  NOT NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_room_user (game_room_id, user_id),
    CONSTRAINT fk_game_actions_room FOREIGN KEY (game_room_id) REFERENCES game_rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_actions_user FOREIGN KEY (user_id)      REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> **uk_game_room_user**: 한 게임에서 유저당 강타 1회만 → 유니크 제약으로 DB 레벨 보장

---

### 3.7 game_records — 전적 기록

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 고유 ID |
| `game_room_id` | BIGINT | FK → game_rooms, NOT NULL | 게임 방 |
| `user_id` | BIGINT | FK → users, NOT NULL | 해당 유저 |
| `opponent_id` | BIGINT | FK → users, NOT NULL | 상대방 |
| `result` | VARCHAR(10) | NOT NULL | WIN / LOSS / DRAW |
| `lp_change` | INT | NOT NULL | LP 변동량 |
| `lp_before` | INT | NOT NULL | 게임 전 LP |
| `lp_after` | INT | NOT NULL | 게임 후 LP |
| `tier_before` | VARCHAR(20) | NOT NULL | 게임 전 티어 |
| `division_before` | VARCHAR(5) | NULLABLE | 게임 전 디비전 |
| `tier_after` | VARCHAR(20) | NOT NULL | 게임 후 티어 |
| `division_after` | VARCHAR(5) | NULLABLE | 게임 후 디비전 |
| `is_promotion_game` | BOOLEAN | NOT NULL, DEFAULT FALSE | 승급전 경기 여부 |
| `created_at` | DATETIME | NOT NULL | 기록일시 |
| `updated_at` | DATETIME | NOT NULL | 수정일시 |

```sql
CREATE TABLE game_records (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    game_room_id       BIGINT      NOT NULL,
    user_id            BIGINT      NOT NULL,
    opponent_id        BIGINT      NOT NULL,
    result             VARCHAR(10) NOT NULL,
    lp_change          INT         NOT NULL,
    lp_before          INT         NOT NULL,
    lp_after           INT         NOT NULL,
    tier_before        VARCHAR(20) NOT NULL,
    division_before    VARCHAR(5)  NULL,
    tier_after         VARCHAR(20) NOT NULL,
    division_after     VARCHAR(5)  NULL,
    is_promotion_game  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_room_user (game_room_id, user_id),
    INDEX idx_user_id_created (user_id, created_at DESC),
    INDEX idx_user_id_result (user_id, result),
    CONSTRAINT fk_game_records_room     FOREIGN KEY (game_room_id) REFERENCES game_rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_records_user     FOREIGN KEY (user_id)      REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_records_opponent FOREIGN KEY (opponent_id)   REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> **인덱스 설명**
> - `idx_user_id_created`: 유저 전적 최신순 조회 (프로필 페이지)
> - `idx_user_id_result`: 유저별 승/패/무 카운트 집계

---

## 4. Redis 저장 구조 (참고)

MySQL이 아닌 **Redis에서 관리**하는 데이터:

| 키 패턴 | 타입 | 용도 | TTL |
|--------|------|------|-----|
| `match:queue:{tierScore}` | Sorted Set | 매칭 큐 (score = 대기 시작 시각) | - |
| `match:accept:{matchId}` | Hash | 매칭 수락 상태 | 15초 |
| `game:session:{gameRoomId}` | Hash | 진행 중 게임 세션 | 60초 |
| `user:session:{userId}` | String | JWT 세션 / 로그인 상태 | 24시간 |
| `rtt:{userId}:{gameRoomId}` | List | RTT 측정값 (5개) | 60초 |

> 매칭 이력은 별도 저장하지 않음 — 매칭 성사 시 game_rooms에 기록되므로 별도 테이블 불필요

---

## 5. 테이블 요약

| # | 테이블 | 행 수 증가 패턴 | 가변/불변 | 설명 |
|---|--------|----------------|----------|------|
| 1 | `users` | 유저당 1행 | Mutable (Soft Delete) | 계정 정보 — 탈퇴 시 익명화 보존 |
| 2 | `social_accounts` | 유저당 0~2행 | 생성/삭제 | 소셜 연동 |
| 3 | `user_rank_info` | 유저당 1행 | Mutable | 현재 랭크 (매 게임마다 갱신) |
| 4 | `promotion_series` | 승급전마다 1행 | 진행 중 Mutable → 완료 후 Immutable | 승급전 이력 |
| 5 | `game_rooms` | 게임당 1행 | 진행 중 Mutable → 종료 후 Immutable | 게임 메타데이터 + 시나리오 |
| 6 | `game_actions` | 게임당 0~2행 | **Immutable** | 강타 판정 상세 기록 |
| 7 | `game_records` | 게임당 2행 | **Immutable** | 전적 기록 (LP 변동 포함) |

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-04-17 | 초안 작성 — 7개 테이블 + Redis 저장 구조 |
| 2026-04-17 | Mermaid ER 파싱 오류 수정, 객체 생명주기 섹션 추가 |
| 2026-04-17 | 모든 테이블에 created_at/updated_at 통일, promotion_series.wins 제거, ER 다이어그램 PK/FK만 표시로 축소 |
| 2026-04-18 | Soft Delete 전환: users에 status/withdrawn_at 추가, 탈퇴 익명화 생명주기 반영, FK ON DELETE RESTRICT 명시, demotion_shield 승리 시 해제 추가 |
