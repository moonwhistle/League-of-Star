# Matching System Implementation Specification - V1 (Stage 1)

이 문서는 **Stage 1: 글로벌 락 + 인메모리 일괄 처리** 방식의 세부 구현 지침을 정의합니다.

---

## 1. 전체 프로세스 흐름 (Overall Flow)

매칭 요청부터 엔진의 탐색, 성사 알림까지의 전체 생명주기입니다.

```mermaid
sequenceDiagram
    participant Client as 유저 (Browser/App)
    participant API as API Server (smite-api)
    participant Redis as Redis (ZSET/HASH)
    participant Engine as MatchEngine (smite-matching)

    Note over Client, Redis: [Step 1: 매칭 신청]
    Client->>API: POST /api/v1/match/join
    API->>Redis: ZADD matching:queue (티어 점수)
    API->>Redis: HSET matching:tickets (상세 정보)
    API-->>Client: 200 OK (대기 시작)

    Note over Redis, Engine: [Step 2: 인메모리 매칭 스캔 (1초 주기)]
    loop Every 1 Second
        Engine->>Redis: SETNX matching:lock (글로벌 락 획득)
        Engine->>Redis: HGETALL matching:tickets (전체 티켓 로드)
        Engine->>Engine: 자바 메모리에서 FIFO 정렬 및 짝짓기
        Engine->>Redis: ZREM/HDEL (매칭 유저 일괄 제거)
        Engine->>Redis: DEL matching:lock (락 해제)
    end

    Note over Client, Engine: [Step 3: 매칭 완료 통보]
    Engine->>API: MatchFoundEvent 발행
    API->>Client: WebSocket (match.found) 알림 발송
```

---

## 2. 데이터 구조 상세 (Data Structures)

### 2.1 Redis Key 설계
| 구분 | Key 명칭 | 데이터 타입 | 주요 필드 / 값 |
| :--- | :--- | :--- | :--- |
| **대기열** | `matching:queue` | Sorted Set | **Member**: `userId`<br>**Score**: `tierScore` (1~28) |
| **티켓** | `matching:tickets` | Hash | **Field**: `userId`<br>**Value**: `{userId, tierScore, entryTime}` (JSON) |
| **글로벌 락** | `matching:lock` | String | **Value**: `locked` (TTL: 5s) |

### 2.2 매칭 티켓 객체 (Java)
```java
public record MatchTicket(
    Long userId,
    int tierScore,
    long entryTime
) {}
```

---

## 3. 엔진 내부 매칭 알고리즘 (Internal Logic)

매칭 엔진 워커는 1초마다 깨어나 아래 단계를 **원자적**으로 수행합니다.

### 3.1 단계별 로직 (Flowchart)

```mermaid
flowchart TD
    Start[1초 주기 기상] --> Lock[Redisson 글로벌 락 획득]
    Lock --> Fetch[Redis에서 모든 티켓 HGETALL 로드]
    Fetch --> Sort[entryTime 기준 오름차순 정렬 - FIFO]
    Sort --> Loop[유저 목록 순회 시작]
    
    Loop --> PickA[유저A 선택]
    PickA --> Calc[대기 시간 계산 및 티어 범위 결정]
    Calc --> FindB[리스트 내 다른 유저 중 범위 내 최적 상대B 탐색]
    
    FindB -- 상대 찾음 --> Mark[A, B를 '매칭됨'으로 마킹]
    FindB -- 상대 없음 --> Next[다음 유저로 이동]
    
    Mark --> Next
    Next --> Done{모든 유저 확인?}
    Done -- No --> Loop
    Done -- Yes --> BatchDelete[매칭된 ID들 Redis에서 일괄 제거]
    
    BatchDelete --> Unlock[락 해제 및 알림 이벤트 발행]
    Unlock --> End[엔진 취침]
```

### 3.2 매칭 범위 확장 정책 (Sliding Window)
- **0 ~ 10초**: 본인 티어 점수 `±1`
- **11 ~ 20초**: 본인 티어 점수 `±2`
- **21 ~ 30초**: 본인 티어 점수 `±4`
- **31초 이상**: 본인 티어 점수 `±8` (최대 범위)

---

## 4. 동시성 및 정합성 보장 전략

1. **글로벌 락 (Redisson)**: `tryLock`을 사용하여 한 번에 하나의 엔진만 대기열을 처리하게 함으로써 '중복 매칭'을 원천 봉쇄합니다.
2. **원자적 삭제 검증**: `ZREM` 명령어의 리턴값(삭제된 개수)을 확인합니다. 만약 2명이 지워져야 하는데 1명만 지워졌다면, 그 사이 유저가 매칭을 취소한 것이므로 해당 매칭은 무효 처리하고 남은 1명은 다음 주기에 다시 매칭합니다.
3. **상태 불변성**: 자바 메모리에서 짝을 지을 때, 이미 매칭된 유저는 별도의 `Set<Long> matchedIds`에 보관하여 한 루프 내에서 중복 선택되지 않도록 합니다.
