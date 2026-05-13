# SSE MVC 동작 흐름

이 문서는 현재 MVC 기반 SSE 매칭 알림 흐름을 정리합니다.

핵심 정책은 다음과 같습니다.

- 클라이언트는 매칭 시작 화면에 진입하면 먼저 SSE 연결을 생성합니다.
- 서버는 인증된 유저 ID 기준으로 SSE 연결을 현재 API 인스턴스 메모리에 저장합니다.
- 연결이 유지되는 동안 서버는 주기적으로 `heartbeat` 이벤트를 보냅니다.
- 매칭 엔진이 매칭을 성사시키면 `MatchFoundEvent`가 발생합니다.
- `MatchFoundEvent`는 Redis Pub/Sub channel로 publish됩니다.
- 모든 API 인스턴스는 같은 Pub/Sub 메시지를 subscribe합니다.
- 각 API 인스턴스는 자기 메모리의 `SseConnectionRegistry`에 연결된 유저에게만 `match_found` SSE 이벤트를 전송합니다.

```mermaid
sequenceDiagram
    autonumber
    actor UserA as User A Client
    actor UserB as User B Client
    participant Api1 as smite-api-1
    participant Api2 as smite-api-2
    participant Registry1 as SseConnectionRegistry(api-1)
    participant Registry2 as SseConnectionRegistry(api-2)
    participant Queue as Match Queue API
    participant Engine as MatchEngine
    participant Found as MatchFoundService
    participant Publisher as MatchFoundPubSubPublisher
    participant Redis as Redis Pub/Sub
    participant Sub1 as MatchFoundPubSubSubscriber(api-1)
    participant Sub2 as MatchFoundPubSubSubscriber(api-2)
    participant Sender1 as MatchFoundSseSender(api-1)
    participant Sender2 as MatchFoundSseSender(api-2)

    UserA->>Api1: GET /api/v1/notifications/match/stream
    Api1->>Api1: JWT 인증 후 userA 식별
    Api1->>Registry1: userA SSE 연결 등록
    Api1-->>UserA: event: connected

    UserB->>Api2: GET /api/v1/notifications/match/stream
    Api2->>Api2: JWT 인증 후 userB 식별
    Api2->>Registry2: userB SSE 연결 등록
    Api2-->>UserB: event: connected

    loop SSE 연결 유지
        Api1-->>UserA: event: heartbeat
        Api2-->>UserB: event: heartbeat
    end

    UserA->>Queue: POST /api/v1/match/join
    UserB->>Queue: POST /api/v1/match/join
    Queue->>Queue: Redis 대기열에 ticket 저장

    Engine->>Queue: 배치 스캔
    Engine->>Queue: atomicPairRemove(userA, userB)
    Queue-->>Engine: userA/userB 원자 제거 성공

    Engine->>Found: process(userA, userB)
    Found->>Found: match:session:{matchId} 저장
    Found->>Publisher: MatchFoundEvent 발행
    Publisher->>Redis: publish notification:match_found

    Redis-->>Sub1: match_found 메시지 수신
    Redis-->>Sub2: match_found 메시지 수신

    Sub1->>Sender1: userA/userB send
    Sender1->>Registry1: userA 연결 조회
    Registry1-->>Sender1: userA 연결 있음
    Sender1->>Registry1: userB 연결 조회
    Registry1-->>Sender1: userB 연결 없음
    Sender1-->>UserA: event: match_found

    Sub2->>Sender2: userA/userB send
    Sender2->>Registry2: userA 연결 조회
    Registry2-->>Sender2: userA 연결 없음
    Sender2->>Registry2: userB 연결 조회
    Registry2-->>Sender2: userB 연결 있음
    Sender2-->>UserB: event: match_found
```

## 흐름 해석

Pub/Sub 메시지는 모든 API 인스턴스가 받습니다. 하지만 SSE 연결 객체는 각 API 인스턴스의 메모리에만 존재합니다.

따라서 `smite-api-1`은 자기 인스턴스에 연결된 `userA`에게만 전송하고, `smite-api-2`는 자기 인스턴스에 연결된 `userB`에게만 전송합니다.

이 구조 덕분에 매칭 엔진이 어느 API 인스턴스에서 실행되더라도, 다른 인스턴스에 연결된 사용자에게도 `match_found` 알림을 전달할 수 있습니다.

## 간단 흐름

```mermaid
flowchart TD
    A[사용자 매칭 화면 진입] --> B[SSE 연결 생성]
    B --> C[API 인스턴스 메모리에<br/>유저별 SSE 연결 저장]
    C --> D[connected 이벤트 전송]
    D --> E[heartbeat 주기 전송]

    A --> F[joinQueue 요청]
    F --> G[Redis 매칭 대기열 저장]
    G --> H[매칭 엔진 배치 스캔]
    H --> I[두 유저 원자 제거]
    I --> J[매칭 세션 저장]
    J --> K[MatchFoundEvent 발행]
    K --> L[Redis Pub/Sub publish]
    L --> M[모든 API 인스턴스 subscribe]
    M --> N[각 인스턴스가<br/>자기 SSE 연결만 조회]
    N --> O[연결된 유저에게<br/>match_found 전송]
```

요약하면, 클라이언트는 매칭 시작과 동시에 SSE 연결을 열어두고 서버의 `heartbeat`를 받습니다. 이후 매칭이 성사되면 서버는 Redis Pub/Sub으로 모든 API 인스턴스에 `match_found` 메시지를 전파하고, 각 인스턴스는 자기에게 연결된 유저에게만 알림을 보냅니다.
