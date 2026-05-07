# Issue-28: 매칭 알림 기능 구현 (SSE V1)

## 📌 Feature Description

매칭 엔진에서 매칭이 성사되었을 때, 대상 유저 2명에게 실시간으로 매칭 성사 알림을 전달하는 SSE(Server-Sent Events) 기반 알림 기능을 구현합니다.

이번 이슈의 목표는 **매칭 성사 이벤트를 클라이언트까지 안정적으로 전달하는 실시간 이벤트 스트림 구조를 만드는 것**입니다. 클라이언트는 매칭 대기 화면에서 SSE 연결을 열고, 서버는 `MatchFoundEvent`가 발생하면 해당 유저의 SSE 연결로 `match_found` 이벤트를 전송합니다.

수락/거절 API, 양쪽 수락 완료 처리, 거절/타임아웃 상태 동기화는 이번 이슈에서 구현하지 않습니다. 해당 기능은 후속 이슈인 `[FEAT] 매칭 수락/거절 API 및 상태 동기화 구현`에서 처리합니다.

---

## 📌 Summary

매칭 알림은 서버에서 클라이언트로 전달되는 단방향 이벤트입니다. 클라이언트가 서버에 지속적으로 입력을 보내는 구조가 아니므로, WebSocket보다 SSE가 현재 요구사항에 더 적합합니다.

SSE는 HTTP 기반이라 인증, 로깅, 장애 분석이 단순하고, 브라우저의 `EventSource`가 자동 재연결을 지원합니다. 매칭 성사처럼 서버가 특정 순간에 이벤트를 push해야 하는 기능에 잘 맞습니다.

이번 이슈에서는 API 모듈에 SSE 연결 API를 추가하고, 유저별 SSE 연결을 관리하는 컴포넌트를 구성합니다. 이후 매칭 모듈에서 발행하는 `MatchFoundEvent`를 API 모듈에서 구독하여 대상 유저 2명에게 매칭 성사 알림을 전송합니다.

V1은 기존 API 서버가 `spring-boot-starter-web` 기반이라는 점을 고려해 MVC `SseEmitter` 방식으로 먼저 구현합니다. 이후 동시 SSE 연결 부하 테스트를 통해 Thread, Heap, CPU, 연결 유지율을 측정하고, 10,000명 동시 연결에서 리소스 한계가 확인되면 WebFlux/Netty 기반 SSE로 전환합니다.

## ⚖️ SSE vs WebSocket 선택 이유

이번 매칭 알림 기능은 클라이언트가 서버에 계속 메시지를 보내는 구조가 아닙니다. 서버에서 매칭 성사 이벤트가 발생하면, 서버가 클라이언트에게 `match_found` 이벤트를 보내는 단방향 알림 구조입니다.

정책적으로 이번 매칭 흐름은 역할을 분리합니다.

```text
SSE  = 서버 -> 클라이언트 실시간 알림
HTTP = 클라이언트 -> 서버 명령 요청
```

즉 서버가 먼저 알려야 하는 `match_found`는 SSE로 전달하고, 클라이언트가 선택하는 `accept`, `reject`는 후속 이슈에서 일반 HTTP API로 처리합니다.

매칭 1건에서 클라이언트가 서버로 보내는 응답은 사실상 수락 또는 거절 1회입니다. 이 한 번의 응답을 위해 WebSocket 양방향 메시지 체계를 도입하면, 얻는 이점보다 백엔드가 관리해야 할 연결 상태와 메시지 프로토콜이 더 커집니다.

따라서 이번 이슈에서는 WebSocket보다 SSE를 우선 선택합니다.

| 비교 항목 | SSE | WebSocket |
| --- | --- | --- |
| 통신 방향 | 서버 -> 클라이언트 단방향에 적합 | 서버 <-> 클라이언트 양방향에 적합 |
| 현재 매칭 알림 요구사항 | 매칭 성사 알림 push에 충분 | 기능 대비 과한 선택일 수 있음 |
| 클라이언트 요청 처리 | `joinQueue`, `accept`, `reject`는 HTTP API로 분리 | 같은 연결로 처리 가능하지만 메시지 프로토콜 설계가 필요 |
| 구현 복잡도 | 비교적 단순 | 연결, 메시지 타입, 세션, 재연결 처리가 더 복잡 |
| 브라우저 지원 | `EventSource` 자동 재연결 지원 | 직접 재연결/heartbeat/ping-pong 설계 필요 |
| 운영/디버깅 | HTTP 기반이라 로그, 인증, 프록시 구성이 단순 | 별도 WebSocket 연결 운영 고려 필요 |
| 게임 플레이 실시간성 | 빠른 양방향 입력에는 부적합 | 실시간 게임 플레이/채팅에 적합 |

### 백엔드 자원 관점

SSE와 WebSocket은 둘 다 연결을 일정 시간 유지하므로, 연결 유지 비용 자체는 둘 다 존재합니다.

하지만 이번 요구사항에서 WebSocket을 선택하면 아래 관리 비용이 추가됩니다.

- WebSocket 세션 저장
- 유저 ID와 WebSocket 연결 매핑
- 연결 끊김 감지
- ping/pong heartbeat
- 재연결 처리
- 메시지 타입 라우팅
- 잘못된 메시지 검증
- 인증 만료 처리
- 서버 여러 대 환경에서 세션 라우팅 또는 pub/sub 고려

반면 SSE + HTTP 구조는 책임이 단순합니다.

- SSE 연결은 매칭 성사 알림을 받기 위한 통로로만 사용합니다.
- 수락/거절은 기존 HTTP 인증, 검증, 예외 처리 흐름을 그대로 사용합니다.
- 클라이언트 명령을 처리하기 위해 별도의 WebSocket 메시지 프로토콜을 만들 필요가 없습니다.

따라서 이번 단계에서는 **수락/거절 1회 응답을 받기 위해 WebSocket을 유지하는 것보다, SSE로 알림을 받고 HTTP로 응답하는 구조가 더 단순하고 효율적**입니다.

### 이번 이슈에서 SSE가 적합한 이유

- 매칭 성사 알림은 서버에서 클라이언트로 보내는 단방향 이벤트입니다.
- 수락/거절 요청은 HTTP API로 처리할 예정이므로, 양방향 연결이 꼭 필요하지 않습니다.
- 브라우저의 `EventSource`가 자동 재연결을 지원해 클라이언트 구현이 단순합니다.
- HTTP 기반 스트림이라 기존 인증/로깅/모니터링 흐름과 맞추기 쉽습니다.
- 매칭 대기 화면에서만 연결을 유지하면 되므로 연결 생명주기가 비교적 명확합니다.

### WebSocket을 쓰는 것이 더 나은 경우

- 실제 게임 플레이 중 클라이언트 입력을 서버에 계속 보내야 하는 경우
- 실시간 채팅처럼 양방향 메시지가 자주 오가는 경우
- 방 내부 상태, 위치, 액션, 타이밍을 낮은 지연으로 계속 동기화해야 하는 경우
- 서버와 클라이언트가 같은 연결에서 복잡한 명령을 주고받아야 하는 경우

이번 이슈는 **매칭 대기 중 match_found 알림을 안정적으로 받는 것**이 목표이므로 SSE를 선택합니다. 실제 게임 플레이 동기화가 필요해지는 단계에서는 WebSocket 또는 별도 게임 서버 구조를 다시 검토합니다.

## 🧭 SSE 구현 단계 전략

이번 이슈에서는 처음부터 WebFlux/Netty를 도입하지 않고, 기존 MVC 구조와 잘 맞는 `SseEmitter`로 V1을 구현합니다.

이유는 다음과 같습니다.

- 현재 API 모듈은 MVC 기반이므로 기존 인증, 컨트롤러, 테스트 구조와 바로 통합할 수 있습니다.
- 이번 이슈의 핵심은 Netty 도입이 아니라, 매칭 성사 이벤트가 클라이언트까지 안정적으로 전달되는지 검증하는 것입니다.
- 먼저 기능을 완성한 뒤 동시 연결 부하 테스트로 실제 병목을 확인해야 전환 근거가 명확해집니다.

단, 목표 사용자 규모를 10,000명으로 잡고 있으므로 SSE 연결 부하 테스트는 반드시 진행합니다.

```text
1단계: MVC SseEmitter 기반 SSE V1 구현
2단계: 1,000 / 5,000 / 10,000 동시 SSE 연결 부하 테스트
3단계: Thread, Heap, CPU, 연결 유지율, 이벤트 전송 지연 측정
4단계: MVC SSE 한계가 확인되면 WebFlux/Netty 기반 SSE로 전환
```

### MVC SSE와 Netty SSE 판단 기준

| 기준 | MVC SseEmitter | WebFlux/Netty SSE | V1 판단 |
| --- | --- | --- | --- |
| 기존 API 구조와의 호환성 | 높음 | 낮음 | MVC 우선 |
| 구현 속도 | 빠름 | 느림 | MVC 우선 |
| 기존 Security/JWT 연동 | 단순 | 별도 검토 필요 | MVC 우선 |
| 동시 장기 연결 확장성 | 상대적으로 불리 | 유리 | 부하 테스트 후 판단 |
| 포트폴리오 개선 스토리 | 기능 구현 중심 | 성능 개선 중심 | 측정 후 전환 시 설득력 높음 |

따라서 이번 이슈의 정책은 다음과 같습니다.

```text
V1: MVC SseEmitter로 SSE 알림 기능을 먼저 완성한다.
V2: 동시 연결 부하 테스트에서 리소스 한계가 확인되면 WebFlux/Netty로 전환한다.
```

### SSE V1 구현 방식 결정 결과

현재 코드 기준으로 V1은 MVC `SseEmitter` 기반으로 구현합니다.

확인 결과 `smite-api`는 `spring-boot-starter-web` 기반 MVC 애플리케이션입니다. WebFlux 의존성은 없고, Security도 Servlet 기반 `SecurityFilterChain`, `OncePerRequestFilter` 구조로 구성되어 있습니다. 따라서 WebFlux/Netty를 이번 이슈에 바로 도입하면 기존 MVC API, Security, RestDocs/MockMvc 테스트 구조와 섞이면서 변경 범위가 커집니다.

반면 MVC `SseEmitter`는 현재 구조와 바로 맞습니다. 기존 JWT 필터가 `Authorization: Bearer` 토큰을 검증하고, `@AuthUser` argument resolver가 `SecurityContextHolder`에서 인증 정보를 읽어 유저 ID를 추출하므로 SSE 연결 API에서도 같은 인증 흐름을 재사용할 수 있습니다.

다만 SSE는 장기 연결이므로 timeout 설정은 구현 단계에서 별도로 확인합니다. 현재 `application.yml`에는 `spring.mvc.async.request-timeout`, `server.tomcat.*` SSE 전용 설정이 없습니다. 따라서 구현 시 다음 설정을 검토합니다.

- `spring.mvc.async.request-timeout`
- `server.tomcat.threads.max`
- `server.tomcat.max-connections`
- SSE heartbeat 주기
- `SseEmitter` timeout 값

결론은 다음과 같습니다.

```text
Issue-28 V1은 MVC SseEmitter로 구현한다.
이유는 기존 API/Security 구조와 가장 잘 맞고, 매칭 성사 알림 정책을 빠르게 검증할 수 있기 때문이다.
동시 SSE 연결 부하 테스트에서 리소스 한계가 확인되면 WebFlux/Netty 전환을 후속 개선으로 진행한다.
```

## 📚 Changes

- API 모듈에 SSE 연결 엔드포인트를 추가합니다.
  - 예: `GET /api/v1/notifications/match/stream`
  - 인증된 유저만 연결할 수 있게 구성합니다.
  - 연결된 유저 ID를 기준으로 SSE 세션을 관리합니다.

- 유저별 SSE 연결 저장소를 구현합니다.
  - 유저 ID별 활성 SSE 연결을 저장합니다.
  - 같은 유저가 재연결하면 기존 연결을 종료하고 새 연결로 교체합니다.
  - 연결 종료, 타임아웃, 전송 실패 시 연결 정보를 제거합니다.

- 매칭 성사 이벤트 전송 흐름을 구현합니다.
  - `MatchFoundEvent`를 이벤트 리스너에서 수신합니다.
  - `userA`, `userB` 각각에게 `match_found` 이벤트를 전송합니다.
  - payload에는 `matchId`, 상대 유저 ID, 수락 제한 시간(`acceptTimeoutSeconds`)을 포함합니다.

- SSE 연결 안정성 처리를 추가합니다.
  - 일정 주기로 heartbeat 이벤트를 전송해 연결이 살아 있는지 확인합니다.
  - 끊어진 연결에 이벤트 전송 시 예외를 처리하고 연결 저장소에서 제거합니다.
  - 이벤트 전송 실패가 매칭 상태 자체를 망가뜨리지 않도록 Redis 매칭 세션을 기준 상태로 유지합니다.

- SSE V1은 MVC `SseEmitter` 기반으로 구현합니다.
  - 기존 API/Security 구조와 빠르게 통합합니다.
  - 동시 SSE 연결 부하 테스트로 Thread, Heap, CPU, 연결 유지율을 측정합니다.
  - 측정 결과 한계가 확인되면 WebFlux/Netty 기반 SSE 전환을 후속 이슈로 분리합니다.

## 📝 Note

- 이번 이슈는 "매칭 성사 알림을 클라이언트에 보내는 것"까지만 다룹니다.
- 수락/거절 버튼을 눌렀을 때 호출할 API는 후속 이슈로 분리합니다.
- 상대방이 수락했는지, 거절했는지, 양쪽 수락이 완료됐는지 알려주는 이벤트도 후속 이슈에서 다룹니다.
- SSE 연결은 알림 전달 경로일 뿐, 매칭 상태의 원본 데이터는 Redis의 `match:session:{matchId}`와 유저 매칭 상태입니다.
- 클라이언트가 일시적으로 SSE 이벤트를 받지 못해도, 후속 API에서 Redis 세션 기준으로 현재 매칭 상태를 조회할 수 있어야 합니다.

## 🔁 SSE 과정 흐름

```mermaid
sequenceDiagram
    autonumber
    participant ClientA as Client A
    participant ClientB as Client B
    participant API as smite-api
    participant Registry as SseConnectionRegistry
    participant Engine as MatchEngine
    participant Found as MatchFoundService
    participant Publisher as MatchFoundPubSubPublishListener
    participant Subscriber as MatchFoundPubSubSubscriber
    participant Dispatcher as MatchFoundNotificationDispatcher
    participant Redis as Redis

    ClientA->>API: GET /api/v1/notifications/match/stream
    API->>API: JWT 인증 후 userA 식별
    API->>Registry: userA SSE 연결 등록
    API-->>ClientA: event: connected

    ClientB->>API: GET /api/v1/notifications/match/stream
    API->>API: JWT 인증 후 userB 식별
    API->>Registry: userB SSE 연결 등록
    API-->>ClientB: event: connected

    loop 연결 유지
        API-->>ClientA: event: heartbeat
        API-->>ClientB: event: heartbeat
    end

    Engine->>Redis: matching:queue:* 스캔
    Engine->>Redis: atomic_pair_remove.lua
    Redis-->>Engine: userA/userB 원자 제거 성공

    Engine->>Found: process(userA, userB)
    Found->>Redis: userA/userB 상태 FOUND 저장
    Found->>Redis: match:session:{matchId} 저장, TTL 12초
    Found->>API: MatchFoundEvent 발행
    API->>Publisher: MatchFoundEvent 수신
    Publisher->>Redis: notification:match_found publish
    Redis-->>Subscriber: 모든 API 인스턴스가 메시지 수신
    Subscriber->>Dispatcher: match_found 전송 위임
    Dispatcher->>Registry: userA SSE 연결 조회
    Dispatcher->>Registry: userB SSE 연결 조회
    Dispatcher-->>ClientA: event: match_found
    Dispatcher-->>ClientB: event: match_found

    alt 특정 유저 SSE 연결 없음
        Dispatcher->>Dispatcher: 전송 스킵 및 로그 기록
    else 이벤트 전송 실패
        Dispatcher->>Registry: 실패한 연결 제거
        Dispatcher->>Dispatcher: 실패 로그 기록
    end
```

### 흐름 요약

1. 클라이언트는 매칭 대기 화면에 진입하면 SSE 스트림을 먼저 연결합니다.
2. 서버는 인증된 유저 ID를 기준으로 SSE 연결을 저장합니다.
3. 매칭 엔진이 두 유저를 원자적으로 큐에서 제거합니다.
4. `MatchFoundService`가 유저 상태와 매칭 세션을 Redis에 저장합니다.
5. `MatchFoundEvent`가 발행됩니다.
6. API 모듈의 Pub/Sub publisher가 `notification:match_found` channel로 메시지를 발행합니다.
7. 모든 API 인스턴스의 subscriber가 메시지를 수신합니다.
8. 각 인스턴스는 자기 `SseConnectionRegistry`에서 두 유저의 SSE 연결을 조회합니다.
9. 연결이 살아 있으면 `match_found` 이벤트를 전송합니다.
10. 연결이 없거나 전송에 실패하면 알림 실패만 로그로 남기고, 매칭 상태는 Redis 기준으로 유지합니다.

### 멀티 인스턴스 한계

현재 V1 구현은 로컬 메모리 기반 SSE registry와 로컬 Spring `ApplicationEvent`를 사용합니다.

```text
SSE 연결 저장소 = 각 API 인스턴스 메모리
MatchFoundEvent = 이벤트가 발생한 JVM 내부에서만 전달
```

따라서 API 인스턴스가 2대 이상이면 아래 문제가 생깁니다.

```text
userA SSE 연결 -> smite-api-1
userB SSE 연결 -> smite-api-2

매칭 엔진 실행 -> smite-api-1
MatchFoundEvent 발행 -> smite-api-1 JVM 내부

smite-api-1은 userA 연결만 찾을 수 있음
smite-api-2에 붙은 userB 연결은 찾을 수 없음
```

부하 테스트에서도 10,000명 연결은 안정적으로 유지됐지만, `match_found`는 이벤트가 발생한 인스턴스에 연결된 유저에게만 전달되는 한계가 확인되었습니다.

이 문제는 MVC `SseEmitter` 자체의 스레드 병목이 아니라 **멀티 인스턴스 간 알림 이벤트 전파 부재**입니다. Netty/WebFlux로 전환해도 각 인스턴스 메모리에 SSE 연결을 저장하는 구조라면 동일한 문제가 발생합니다.

따라서 다음 개선은 Netty 전환이 아니라 Redis Pub/Sub 기반의 인스턴스 간 `match_found` 전파 구조입니다.

```text
MatchFoundEvent 발생
-> Redis Pub/Sub channel publish
-> 모든 API 인스턴스가 subscribe
-> 각 인스턴스가 자기 메모리 registry에서 대상 유저 연결 조회
-> 연결이 있는 인스턴스만 SSE 전송
```

## 📌 Related Issue
- Closes #28

---

## 📚 Tasks

### 1. SSE V1 구현 방식 결정
- [x] **현재 API 서버 구조 확인**
  - `smite-api`가 현재 `spring-boot-starter-web` 기반으로 동작하는지 확인
  - 기존 MVC 컨트롤러, Spring Security, RestDocs/OpenAPI 구성과 함께 `SseEmitter`를 사용할 수 있는지 확인
  - SSE 연결 timeout, async request timeout, Tomcat connection 설정 확인

- [x] **SSE 구현 방식 결정**
  - V1은 Spring MVC `SseEmitter` 기반으로 구현
  - 이유: 기존 API 구조와의 호환성, 빠른 검증, 낮은 구현 위험
  - WebFlux/Netty 기반 SSE는 동시 연결 부하 테스트 이후 후속 개선으로 검토

### 2. SSE 연결 API 구현
- [x] **SSE 연결 엔드포인트 추가**
  - `GET /api/v1/notifications/match/stream`
  - 인증된 유저 ID를 `@AuthUser` 또는 SecurityContext에서 추출
  - 응답 Content-Type은 `text/event-stream` 형태로 제공

- [x] **초기 연결 이벤트 전송**
  - 연결 직후 `connected` 이벤트 전송
  - 클라이언트가 정상 연결 여부를 바로 알 수 있게 구성
  - 필요 시 현재 서버 시간 또는 connection id 포함

- [x] **인증 실패 처리**
  - 인증되지 않은 요청은 SSE 연결을 열지 않음
  - 기존 인증 실패 응답 정책과 동일하게 처리

#### 구현 결과

- `MatchNotificationController`를 추가해 SSE 연결 API를 제공합니다.
- `MatchNotificationService`에서 `SseEmitter`를 생성하고, 연결 직후 `connected` 이벤트를 전송합니다.
- `connected` payload에는 `userId`, `connectedAt`을 포함합니다.
- 인증은 기존 JWT 필터와 `@AuthUser` resolver를 그대로 사용합니다.
- 인증되지 않은 요청은 기존 Security 정책에 따라 SSE 연결을 열지 않습니다.
- 유저별 연결 저장과 재연결 교체는 Task 3에서 확장합니다.

### 3. 유저별 SSE 연결 관리
- [x] **SSE 연결 저장소 구현**
  - 유저 ID를 key로 활성 연결 저장
  - 저장 구조 예: `ConcurrentHashMap<Long, SseConnection>`
  - 연결 객체는 이벤트 전송, 완료 처리, 종료 처리를 캡슐화

- [x] **재연결 처리**
  - 같은 유저가 새로 연결하면 기존 연결을 종료하고 새 연결로 교체
  - 브라우저 새로고침, 네트워크 재연결 상황에서 중복 연결이 쌓이지 않도록 처리

- [x] **연결 제거 처리**
  - 클라이언트 연결 종료 시 저장소에서 제거
  - 타임아웃 발생 시 저장소에서 제거
  - 이벤트 전송 실패 시 저장소에서 제거

#### 구현 결과

- `SseConnection`을 추가해 유저 1명의 SSE 연결을 표현합니다.
- `SseConnectionRegistry`를 추가해 유저 ID별 활성 연결을 `ConcurrentHashMap`으로 관리합니다.
- 같은 유저가 재연결하면 기존 연결은 `complete()` 처리하고 새 연결로 교체합니다.
- `onCompletion`, `onTimeout`, `onError` 콜백을 등록해 연결 종료 시 저장소에서 제거합니다.
- 오래된 연결의 종료 콜백이 새 연결을 지우지 않도록 `remove(userId, connection)` 방식으로 현재 연결만 제거합니다.
- `MatchNotificationService`는 SSE 연결 생성 후 registry에 등록하고, 초기 `connected` 이벤트 전송 실패 시 연결을 제거합니다.

### 4. Heartbeat 구현
- [x] **주기적 heartbeat 이벤트 전송**
  - 일정 주기로 `heartbeat` 이벤트 전송
  - 프록시, 브라우저, 네트워크 장비가 유휴 연결을 끊지 않도록 유지
  - heartbeat 주기는 설정값으로 분리

- [x] **heartbeat 실패 연결 정리**
  - heartbeat 전송 중 예외 발생 시 해당 연결 제거
  - 실패 연결이 계속 저장소에 남아 메모리 누수가 생기지 않도록 처리

#### 구현 결과

- `notificationTaskScheduler`를 추가해 알림 전용 스케줄러를 분리했습니다.
- 매칭 엔진의 전역 `@Scheduled` 설정과 섞이지 않도록 heartbeat는 `@Scheduled`를 사용하지 않고, 알림 전용 `TaskScheduler`에서 직접 실행합니다.
- `SseHeartbeatService`를 추가해 15초마다 전체 SSE 연결에 `heartbeat` 이벤트를 전송합니다.
- `heartbeat` payload에는 `sentAt`을 포함합니다.
- heartbeat 전송 실패 시 해당 연결을 registry에서 제거하고 `completeWithError()`로 종료합니다.
- `SseConnectionRegistry.findAll()`을 추가해 현재 활성 연결 스냅샷을 순회할 수 있게 했습니다.

### 5. MatchFoundEvent 연동
- [x] **매칭 성사 이벤트 리스너 구현**
  - `MatchFoundEvent`를 구독하는 listener 추가
  - 이벤트 발생 시 `userA`, `userB` 대상 연결 조회
  - 연결이 있으면 각각에게 `match_found` 이벤트 전송

- [x] **매칭 성사 payload 정의**
  - `matchId`
  - `userId`
  - `opponentUserId`
  - `acceptTimeoutSeconds`
  - 필요 시 `eventCreatedAt`

- [x] **미연결 유저 처리**
  - 대상 유저의 SSE 연결이 없으면 이벤트 전송은 스킵
  - 매칭 상태는 Redis 세션에 이미 저장되어 있으므로, 전송 실패만 로그로 남김
  - 재전송/보상 정책은 후속 이슈에서 검토

#### 구현 결과

- `match_found` 이벤트 이름을 추가했습니다.
- `MatchFoundNotification` payload를 추가했습니다.
  - `matchId`
  - `userId`
  - `opponentUserId`
  - `acceptTimeoutSeconds`
  - `eventCreatedAt`
- `MatchFoundPubSubPublishListener`를 추가해 `MatchFoundEvent`를 구독합니다.
- 이벤트 발생 시 Redis Pub/Sub channel에 `match_found` 메시지를 publish합니다.
- 각 API 인스턴스의 subscriber가 메시지를 수신한 뒤 `userA`, `userB` 각각의 SSE 연결을 조회하고, 연결이 있으면 `match_found` 이벤트를 전송합니다.
- 대상 유저의 SSE 연결이 없으면 알림 전송만 스킵하고 매칭 상태는 Redis 세션 기준으로 유지합니다.
- `SseNotificationSender`를 추가해 heartbeat와 match_found가 같은 전송 실패 처리 정책을 사용하도록 분리했습니다.
- 전송 실패 시 실패 연결은 registry에서 제거하고 `completeWithError()`로 종료합니다.

### 6. 전송 실패 및 로깅 처리
- [x] **전송 실패 예외 처리**
  - 끊어진 SSE 연결에 전송 시 예외를 잡고 연결 제거
  - 매칭 성사 처리 흐름 전체가 실패하지 않도록 알림 실패를 격리

- [x] **운영 로그 정리**
  - 연결 생성 로그
  - 연결 종료 로그
  - 매칭 성사 이벤트 전송 성공/실패 로그
  - heartbeat 실패 로그

#### 구현 결과

- `SseNotificationSender`를 공통 전송 컴포넌트로 사용하도록 정리했습니다.
- `connected`, `heartbeat`, `match_found` 이벤트 전송은 모두 같은 실패 처리 정책을 사용합니다.
- 이벤트 전송 중 `IOException`이 발생하면 실패한 연결만 registry에서 제거하고 `completeWithError()`로 종료합니다.
- 알림 전송 실패는 매칭 상태 변경 흐름을 중단시키지 않습니다.
  - 매칭 상태의 기준 데이터는 Redis `match:session:{matchId}`와 유저 매칭 상태입니다.
  - SSE는 클라이언트에게 알려주는 전달 경로이므로, 전송 실패는 로그와 연결 정리로 격리합니다.
- 운영 로그는 다음 기준으로 남깁니다.
  - SSE 연결 등록
  - 같은 유저 재연결로 인한 기존 연결 교체
  - 연결 완료, 타임아웃, 에러로 인한 연결 제거
  - `match_found` 전송 성공
  - 대상 유저의 SSE 연결 없음
  - SSE 이벤트 전송 실패

### 7. API 문서 및 사용 예시 작성
- [x] **SSE 연결 API 문서화**
  - 요청 URL
  - 인증 방식
  - 이벤트 이름
  - 이벤트 payload 예시

- [x] **클라이언트 사용 예시 작성**
  - `EventSource` 연결 예시
  - `match_found` 이벤트 수신 예시
  - 연결 종료/재연결 시 주의사항

#### API 문서

```http
GET /api/v1/notifications/match/stream
Accept: text/event-stream
Authorization: Bearer {accessToken}
```

매칭 대기 화면에서 호출하는 SSE 연결 API입니다. 인증된 유저 ID를 기준으로 서버가 SSE 연결을 저장합니다.

응답은 일반 JSON 응답이 아니라 `text/event-stream` 스트림입니다. 연결이 유지되는 동안 서버가 이벤트를 계속 내려보낼 수 있습니다.

#### 이벤트 목록

| 이벤트 이름 | 발생 시점 | payload |
| --- | --- | --- |
| `connected` | SSE 연결 직후 | `userId`, `connectedAt` |
| `heartbeat` | 연결 유지 확인용 주기 이벤트 | `sentAt` |
| `match_found` | 매칭 엔진에서 매칭 성사 이벤트 발생 시 | `matchId`, `userId`, `opponentUserId`, `acceptTimeoutSeconds`, `eventCreatedAt` |

#### connected 예시

```text
event: connected
data: {
  "userId": 1,
  "connectedAt": "2026-05-04T00:00:00Z"
}
```

#### heartbeat 예시

```text
event: heartbeat
data: {
  "sentAt": "2026-05-04T00:00:15Z"
}
```

#### match_found 예시

```text
event: match_found
data: {
  "matchId": "match-20260504-0001",
  "userId": 1,
  "opponentUserId": 2,
  "acceptTimeoutSeconds": 10,
  "eventCreatedAt": "2026-05-04T00:00:20Z"
}
```

#### 클라이언트 사용 예시

브라우저 기본 `EventSource`는 커스텀 `Authorization` 헤더를 직접 넣을 수 없습니다. 실제 프론트 구현에서는 아래 두 방식 중 하나를 선택해야 합니다.

| 방식 | 설명 | V1 판단 |
| --- | --- | --- |
| 쿠키 기반 인증 | `EventSource`가 쿠키를 자동 포함 | 브라우저 SSE와 가장 단순하게 맞음 |
| fetch 기반 SSE polyfill | `Authorization` 헤더를 직접 설정 가능 | 현재 Bearer 토큰 정책을 유지하기 쉬움 |

현재 API 문서 기준 인증 방식은 `Authorization: Bearer {accessToken}`입니다. 따라서 프론트가 Bearer 토큰을 유지한다면 native `EventSource`만으로는 부족하고, 헤더 설정이 가능한 SSE 클라이언트 라이브러리 또는 fetch 기반 stream 처리가 필요합니다.

```javascript
const eventSource = new EventSource('/api/v1/notifications/match/stream', {
  withCredentials: true,
});

eventSource.addEventListener('connected', (event) => {
  const payload = JSON.parse(event.data);
  console.log('SSE connected', payload);
});

eventSource.addEventListener('heartbeat', (event) => {
  const payload = JSON.parse(event.data);
  console.log('SSE heartbeat', payload.sentAt);
});

eventSource.addEventListener('match_found', (event) => {
  const payload = JSON.parse(event.data);

  openMatchAcceptModal({
    matchId: payload.matchId,
    opponentUserId: payload.opponentUserId,
    acceptTimeoutSeconds: payload.acceptTimeoutSeconds,
  });
});

eventSource.onerror = () => {
  // EventSource는 네트워크 오류 시 자동 재연결을 시도합니다.
  // 화면을 벗어나거나 매칭이 종료되면 명시적으로 close() 해야 합니다.
};
```

#### 연결 종료 기준

- 사용자가 매칭 대기 화면을 벗어나면 클라이언트에서 `eventSource.close()`를 호출합니다.
- 매칭이 성사되어 수락/거절 모달로 넘어간 뒤 더 이상 대기 이벤트가 필요 없으면 연결을 종료합니다.
- 브라우저 새로고침이나 네트워크 재연결로 같은 유저가 다시 연결하면 서버는 기존 연결을 닫고 새 연결로 교체합니다.
- 서버는 연결 완료, 타임아웃, 에러, 전송 실패 시 registry에서 해당 연결을 제거합니다.

#### 구현 결과

- `notification-match-stream` RestDocs 문서를 추가했습니다.
- SSE 연결 URL, 인증 헤더, 이벤트 이름, payload 예시를 문서화했습니다.
- 클라이언트 구현 시 native `EventSource`와 Bearer 토큰 인증의 제약을 명시했습니다.
- 재연결과 화면 이탈 시 연결 종료 기준을 정리했습니다.

### 8. 테스트 코드 작성
- [x] **SSE 연결 저장소 단위 테스트**
  - 연결 등록 테스트
  - 재연결 시 기존 연결 교체 테스트
  - 연결 종료 시 제거 테스트

- [x] **매칭 성사 이벤트 전송 테스트**
  - `MatchFoundEvent` 발생 시 두 유저에게 이벤트가 전송되는지 검증
  - 한 명만 연결된 경우 연결된 유저에게만 전송되는지 검증
  - 두 명 모두 연결되지 않은 경우 예외 없이 종료되는지 검증

- [x] **전송 실패 처리 테스트**
  - 이벤트 전송 중 예외 발생 시 연결이 제거되는지 검증
  - 전송 실패가 매칭 이벤트 처리 전체를 중단시키지 않는지 검증

- [x] **heartbeat 테스트**
  - heartbeat 이벤트가 주기적으로 전송되는지 검증
  - heartbeat 실패 시 연결이 제거되는지 검증

#### 구현 결과

- `MatchNotificationServiceTest`를 추가했습니다.
  - SSE 연결 생성 시 registry에 연결이 저장되는지 검증합니다.
  - 연결 직후 `connected` 이벤트 전송을 요청하는지 검증합니다.
- `SseConnectionRegistryTest`를 보강했습니다.
  - 유저별 연결 등록/조회
  - 재연결 시 기존 연결 종료 후 새 연결 교체
  - 현재 연결만 제거하고 오래된 연결 제거 요청은 무시
  - `onCompletion`, `onTimeout`, `onError` 콜백 실행 시 registry 제거
- `MatchFoundNotificationDispatcherTest`를 작성했습니다.
  - 두 유저 모두 연결된 경우 두 유저에게 `match_found` 전송
  - 한 유저만 연결된 경우 연결된 유저에게만 전송
  - 두 유저 모두 미연결이어도 예외 없이 종료
  - 전송 실패 시 실패 연결 제거
  - 대상 유저 기준 `matchId`, `userId`, `opponentUserId`, `acceptTimeoutSeconds` payload 생성 검증
- `SseNotificationSenderTest`를 추가했습니다.
  - 전송 성공 시 true 반환 및 연결 유지
  - 전송 실패 시 false 반환, registry 제거, `completeWithError()` 호출
- `SseHeartbeatServiceTest`를 작성했습니다.
  - 등록된 연결에 `heartbeat` 이벤트 전송
  - heartbeat 실패 시 실패 연결 제거 및 에러 완료 처리
- `MatchNotificationControllerRestDocsTest`를 추가했습니다.
  - SSE 연결 API 문서화 테스트를 통해 문서 생성 흐름을 검증합니다.

### 9. SSE 부하 테스트
- [x] **부하 테스트 목적 정의**
  - MVC `SseEmitter` 기반 SSE가 목표 사용자 규모에서 안정적으로 연결을 유지할 수 있는지 확인
  - 동시 연결 수 증가에 따른 Thread, Heap, CPU, GC, 연결 유지율 변화를 측정
  - 측정 결과를 바탕으로 WebFlux/Netty 기반 SSE 전환 필요성을 판단

- [x] **동시 SSE 연결 유지 테스트**
  - 1,000 동시 연결: 기본 안정성 검증
  - 5,000 동시 연결: 목표 안정 구간 검증
  - 10,000 동시 연결: 한계 확인 구간 검증
  - 각 연결은 매칭 대기 화면에 머무르는 상황을 가정해 일정 시간 유지

- [x] **heartbeat 안정성 테스트**
  - 연결된 클라이언트에게 주기적으로 `heartbeat` 이벤트 전송
  - heartbeat 전송 성공률 측정
  - heartbeat 실패 시 연결이 저장소에서 제거되는지 확인
  - heartbeat 주기가 서버 리소스에 미치는 영향 측정

- [x] **match_found 이벤트 전송 테스트**
  - 연결된 유저 중 일부에게 `match_found` 이벤트를 전송
  - 이벤트 전송 성공률 측정
  - 이벤트 전송 p95/p99 지연 시간 측정
  - 연결이 끊긴 유저에게 전송 시 실패 연결이 정리되는지 확인

- [x] **관측 지표 정의**
  - SSE 연결 성공률
  - SSE 연결 유지율
  - SSE 연결 종료/실패 수
  - heartbeat 전송 성공률
  - `match_found` 이벤트 전송 성공률
  - `match_found` 이벤트 전송 p95/p99 지연 시간
  - JVM Heap 사용량
  - JVM Thread 수
  - JVM Thread 상태별 수
  - Tomcat current/busy threads
  - Tomcat current connections
  - GC pause
  - Process CPU
  - Tomcat active threads / active connections

- [x] **성공 기준 정의**
  - 1,000 동시 연결은 반드시 안정적으로 유지
  - 5,000 동시 연결은 목표 안정 구간으로 설정
  - 10,000 동시 연결은 MVC SSE의 한계 확인 구간으로 설정
  - 연결 유지율이 낮거나 Thread/Heap/CPU가 급격히 증가하면 WebFlux/Netty 전환 후보로 기록
  - `match_found` 이벤트 전송 실패는 매칭 상태를 변경하지 않고 알림 실패로만 격리

- [x] **부하 테스트 스크립트 작성**
  - SSE 연결 유지용 부하 테스트 스크립트 작성
  - heartbeat 수신 확인 가능 여부 검토
  - `match_found` 이벤트 전송 테스트를 위한 테스트 전용 이벤트 트리거 방식 검토
  - 테스트 결과는 `docs/load-test/result` 하위에 보관

- [x] **결과 분석 문서 작성**
  - 연결 수별 결과를 표로 정리
  - Grafana 캡처 이미지 첨부
  - MVC SSE 유지 가능 여부 판단
  - WebFlux/Netty 전환 필요성 여부 정리

#### 구현 결과

- SSE 전용 Micrometer 지표를 추가했습니다.
  - `sse_notification_connections_active`
  - `sse_notification_connections_opened_total`
  - `sse_notification_connections_closed_total{reason}`
  - `sse_notification_connection_duration_seconds`
  - `sse_notification_events_send_attempts_total{event}`
  - `sse_notification_events_send_success_total{event}`
  - `sse_notification_events_send_failures_total{event}`
  - `sse_notification_event_send_duration_seconds{event,result}`
- 지표 이름은 MVC `SseEmitter`와 향후 WebFlux/Netty SSE가 같은 이름을 사용하도록 구현체와 무관하게 정의했습니다.
- `SseConnectionRegistry`에서 활성 연결 수, 연결 생성 수, 종료 사유, 연결 유지 시간을 기록합니다.
- `SseNotificationSender`에서 이벤트 전송 시도/성공/실패 수와 전송 지연 시간을 기록합니다.
- SSE 전용 부하 테스트 스크립트를 추가했습니다.
  - `docs/load-test/sse-notification-load.mjs`
  - `MODE=connection`: SSE 연결 유지 및 heartbeat 안정성 테스트
  - `MODE=match`: SSE 연결 후 `joinQueue`를 호출해 실제 `match_found` 수신까지 확인
- SSE 전용 Grafana 대시보드를 추가했습니다.
  - `docs/grafana/smite-sse-notification-dashboard.json`
  - 활성 연결 수, 연결 종료 사유, 이벤트 전송 성공/실패율, 전송 p95/p99, JVM Thread/Thread 상태/Heap/CPU, Tomcat Thread/Connection을 확인합니다.
- SSE 부하 테스트 실행법과 MVC vs Netty 비교 기준을 `docs/load-test/README.md`에 정리했습니다.

#### 중요한 판단 기준

Netty 전환 여부는 "10,000명이라서 무조건 Netty"가 아니라 아래 기준으로 판단합니다.

```text
MVC SseEmitter가 10,000 연결에서 active connection, thread, heap, CPU, 이벤트 전송 p95/p99를 안정적으로 유지하는가?
```

유지하지 못하면 WebFlux/Netty로 전환하고, 같은 `sse_notification_*` 지표로 개선 폭을 비교합니다.

### 10. 멀티 인스턴스 match_found 전파 구조 보강
- [x] **현재 로컬 이벤트 구조 한계 정리**
  - `SseConnectionRegistry`가 인스턴스별 메모리에 존재한다는 점 명시
  - Spring `ApplicationEvent`가 같은 JVM 안에서만 전달된다는 점 명시
  - 2대 API 인스턴스 부하 테스트에서 한쪽 인스턴스 연결 유저에게 알림이 누락될 수 있음을 정리

#### 현재 로컬 이벤트 구조 한계 정리 결과

기존 V1 로컬 전송 구조의 `match_found` 알림 흐름은 아래 구조였습니다.

```text
MatchFoundService
-> ApplicationEventPublisher.publishEvent(MatchFoundEvent)
-> 같은 JVM의 로컬 직접 전송 리스너
-> 같은 JVM의 SseConnectionRegistry 조회
-> 연결이 있으면 SseEmitter.send(match_found)
```

코드 기준으로 보면 한계는 명확합니다.

- `SseConnectionRegistry`
  - `ConcurrentHashMap<Long, SseConnection>`으로 유저별 SSE 연결을 저장합니다.
  - 이 map은 Redis가 아니라 각 API 인스턴스의 JVM 메모리에만 존재합니다.
  - 따라서 `smite-api-1`에 연결된 유저는 `smite-api-1`만 전송할 수 있고, `smite-api-2`에서는 해당 연결을 알 수 없습니다.

- `MatchFoundService`
  - 매칭 세션 저장 후 `ApplicationEventPublisher.publishEvent(new MatchFoundEvent(...))`를 호출합니다.
  - Spring `ApplicationEvent`는 현재 애플리케이션 컨텍스트 내부 이벤트입니다.
  - 즉, 이벤트가 발생한 인스턴스의 JVM 안에서만 로컬 리스너가 실행됩니다.

- 기존 로컬 직접 전송 리스너
  - 이벤트를 받으면 자기 인스턴스의 `SseConnectionRegistry`만 조회합니다.
  - 대상 유저가 다른 인스턴스에 SSE 연결되어 있으면 현재 인스턴스 registry에는 없으므로 전송을 스킵합니다.

2대 인스턴스 테스트에서 확인된 현상은 다음과 같습니다.

```text
SSE 연결 10,000명
-> smite-api-1 약 5,000명
-> smite-api-2 약 5,000명

매칭 엔진 실행 인스턴스에서 MatchFoundEvent 발생
-> 해당 인스턴스 registry에 있는 유저에게만 match_found 전송
-> 다른 인스턴스 registry에 있는 유저는 전송 대상에서 누락
```

따라서 현재 한계는 **MVC SseEmitter 성능 한계가 아니라 멀티 인스턴스 이벤트 전파 한계**입니다.

Netty/WebFlux로 전환해도 `SSE 연결 저장소 = 인스턴스 메모리`, `이벤트 = 로컬 JVM 이벤트` 구조가 그대로라면 동일한 문제가 발생합니다. 다음 작업은 SSE 구현체 전환이 아니라 Redis Pub/Sub 기반으로 `match_found` 이벤트를 모든 API 인스턴스에 전파하는 것입니다.

- [x] **Redis Pub/Sub 메시지 모델 정의**
  - channel 이름 상수화
    - 예: `notification:match_found`
  - publish payload 정의
    - `matchId`
    - `userA`
    - `userB`
    - `acceptTimeoutSeconds`
  - `eventCreatedAt`
  - JSON 직렬화/역직렬화 방식 결정
  - 메시지 DTO는 API notification 패키지에 둘지, core 이벤트 모델과 분리할지 결정

#### Redis Pub/Sub 메시지 모델 정의 결과

- channel 이름은 `notification:match_found`로 정의했습니다.
- Pub/Sub 메시지는 SSE payload와 분리했습니다.
  - Pub/Sub 메시지: 매칭 1건을 표현하는 pair-level 메시지
  - SSE payload: 각 유저에게 전달하는 user-level 메시지
- Pub/Sub 메시지 필드는 다음과 같습니다.
  - `matchId`
  - `userA`
  - `userB`
  - `acceptTimeoutSeconds`
  - `eventCreatedAt`
- 메시지 모델은 API 모듈의 `notification.pubsub` 패키지에 둡니다.
  - 이유: Redis Pub/Sub은 API 인스턴스 간 SSE 알림 전파를 위한 애플리케이션 알림 인프라이며, core 도메인 이벤트와는 역할이 다릅니다.
  - core의 `MatchFoundEvent`는 매칭 성사 도메인 이벤트로 유지합니다.
  - Pub/Sub 메시지는 해당 이벤트를 인스턴스 간 전달하기 위한 전송 모델로 사용합니다.
- JSON 직렬화/역직렬화는 Spring Boot가 제공하는 `ObjectMapper`를 사용하는 `MatchFoundPubSubMessageCodec`으로 캡슐화했습니다.

- [x] **match_found publish 구현**
  - `MatchFoundEvent` 발생 시 Redis Pub/Sub channel로 메시지 publish
  - 기존 로컬 직접 전송 경로가 바로 SSE 전송하지 않도록 책임 재정리
  - publish 실패 시 매칭 상태는 Redis 세션 기준으로 유지하고 에러 로그만 남김
  - publish 성공/실패 메트릭 추가

#### match_found publish 구현 결과

- `MatchFoundPubSubPublisher`를 추가했습니다.
  - `StringRedisTemplate.convertAndSend()`로 `notification:match_found` channel에 JSON payload를 publish합니다.
  - payload 직렬화는 `MatchFoundPubSubMessageCodec`을 사용합니다.
- `MatchFoundPubSubPublishListener`를 추가했습니다.
  - 로컬 `MatchFoundEvent`를 수신합니다.
  - `MatchFoundPubSubMessage`로 변환합니다.
  - Redis Pub/Sub channel로 publish합니다.
- publish 실패는 예외를 밖으로 던지지 않고 로그와 메트릭으로 격리합니다.
  - 매칭 상태와 매칭 세션은 이미 Redis에 저장되어 있으므로 publish 실패가 매칭 상태 자체를 되돌리지는 않습니다.
- publish 성공/실패 메트릭을 추가했습니다.
  - `sse_notification_pubsub_publish_success_total{event="match_found"}`
  - `sse_notification_pubsub_publish_failures_total{event="match_found"}`

주의:
- publish 실패는 매칭 상태 자체를 되돌리지 않습니다.
- 매칭 상태와 매칭 세션은 Redis에 이미 저장되어 있으므로, publish 실패는 알림 전파 실패로만 보고 로그와 메트릭으로 추적합니다.
- subscribe 구현 이후 실제 SSE 전송은 Pub/Sub subscribe 경로로 단일화했고, 로컬 직접 전송 리스너는 제거했습니다.

- [x] **API 인스턴스별 subscribe 구현**
  - 모든 API 인스턴스가 `notification:match_found` channel subscribe
  - 메시지 수신 시 자기 인스턴스의 `SseConnectionRegistry`에서 `userA`, `userB` 연결 조회
  - 연결이 있는 유저에게만 `match_found` SSE 전송
  - 연결이 없는 유저는 정상 스킵으로 처리

#### API 인스턴스별 subscribe 구현 결과

- `MatchNotificationPubSubConfig`를 추가했습니다.
  - `RedisMessageListenerContainer`를 생성합니다.
  - `notification:match_found` channel에 `MatchFoundPubSubSubscriber`를 등록합니다.
  - 따라서 모든 API 인스턴스가 동일 channel을 구독합니다.
- `MatchFoundPubSubSubscriber`를 추가했습니다.
  - Redis Pub/Sub 메시지를 수신합니다.
  - JSON payload를 `MatchFoundPubSubMessage`로 decode합니다.
  - `MatchFoundNotificationDispatcher`에 전송 처리를 위임합니다.
  - 메시지 처리 실패는 로그로 격리하고 listener thread 밖으로 예외를 전파하지 않습니다.
- `MatchFoundNotificationDispatcher`를 추가했습니다.
  - 현재 인스턴스의 `SseConnectionRegistry`에서 `userA`, `userB` 연결을 조회합니다.
  - 연결이 있는 유저에게만 `match_found` SSE를 전송합니다.
  - 연결이 없는 유저는 정상 스킵합니다.
- 기존 로컬 직접 전송 리스너인 `MatchFoundEventListener`를 제거했습니다.
  - 이제 전송 흐름은 `MatchFoundEvent -> Redis Pub/Sub publish -> 모든 인스턴스 subscribe -> 로컬 registry 조회 후 전송` 단일 경로입니다.
  - 로컬 이벤트 직접 전송과 Pub/Sub 전송이 동시에 실행되어 중복 알림이 발생하는 문제를 방지합니다.

- [x] **중복 전송 방지 정책 정리**
  - Pub/Sub 메시지는 모든 인스턴스가 받지만, 각 인스턴스는 자기 메모리에 존재하는 연결에만 전송
  - 같은 유저가 재연결하면 registry가 기존 연결을 교체하므로 동일 인스턴스 중복 연결은 방지됨
  - 로컬 이벤트 전송과 Pub/Sub 전송이 동시에 동작해 이중 전송되지 않도록 전송 경로를 하나로 고정

#### 중복 전송 방지 정책 정리 결과

- `MatchFoundEvent`는 더 이상 직접 SSE 전송을 수행하지 않습니다.
- `MatchFoundPubSubPublishListener`는 `MatchFoundEvent`를 Redis Pub/Sub 메시지로 변환해 publish만 담당합니다.
- 실제 SSE 전송은 각 API 인스턴스의 `MatchFoundPubSubSubscriber -> MatchFoundNotificationDispatcher` 경로에서만 수행합니다.
- 모든 API 인스턴스가 같은 Pub/Sub 메시지를 받지만, 각 인스턴스는 자기 JVM 메모리의 `SseConnectionRegistry`에 존재하는 연결에만 전송합니다.
- 따라서 `smite-api-1`에 붙은 유저는 `smite-api-1`에서만, `smite-api-2`에 붙은 유저는 `smite-api-2`에서만 알림을 받습니다.
- 로컬 직접 전송 리스너를 제거했기 때문에 같은 인스턴스에서 Pub/Sub 전송과 로컬 전송이 동시에 실행되어 중복 알림이 발생하지 않습니다.

- [x] **관측 지표 보강**
  - Pub/Sub publish 성공/실패 수
  - Pub/Sub subscribe 수신 수
  - 연결 없음으로 인한 `match_found` 스킵 수
  - 인스턴스별 `match_found` 전송 성공 수
  - 인스턴스별 활성 SSE 연결 수와 `match_found` 전송 수를 함께 볼 수 있도록 Grafana 패널 보강

#### 관측 지표 보강 결과

- Pub/Sub publish 지표를 유지했습니다.
  - `sse_notification_pubsub_publish_success_total{event="match_found"}`
  - `sse_notification_pubsub_publish_failures_total{event="match_found"}`
- Pub/Sub subscribe 지표를 추가했습니다.
  - `sse_notification_pubsub_messages_received_total{event="match_found"}`
  - `sse_notification_pubsub_messages_failures_total{event="match_found", reason="decode|dispatch"}`
- 현재 인스턴스의 로컬 SSE 연결 조회 결과를 분리했습니다.
  - `sse_notification_match_found_dispatch_local_hits_total`
  - `sse_notification_match_found_dispatch_local_misses_total`
- `local_miss`는 멀티 인스턴스 구조에서 정상적으로 발생할 수 있습니다.
  - 모든 API 인스턴스가 같은 Pub/Sub 메시지를 받기 때문입니다.
  - 어떤 유저의 SSE 연결은 한 인스턴스에만 있으므로, 다른 인스턴스에서는 miss가 됩니다.
  - 따라서 실패 판단은 miss 단독이 아니라 `publish 성공`, `인스턴스별 subscribe 수신`, `local hit`, `match_found send success`를 함께 봐야 합니다.
- Grafana SSE 대시보드에 `Redis Pub/Sub 전파` 섹션을 추가했습니다.
  - Pub/Sub publish 성공/실패
  - 인스턴스별 Pub/Sub 수신 수
  - Pub/Sub 처리 실패
  - 로컬 연결 조회 hit/miss

- [x] **테스트 코드 작성**
  - publish payload 생성 테스트
  - subscribe 메시지 수신 시 연결된 유저에게만 전송되는지 테스트
  - 연결 없는 유저는 예외 없이 스킵되는지 테스트
  - 로컬 이벤트와 Pub/Sub 경로가 중복 전송하지 않는지 테스트

#### 테스트 코드 작성 결과

- `MatchFoundPubSubMessageTest`
  - `MatchFoundEvent`에서 Pub/Sub 메시지를 생성하는 payload 변환을 검증합니다.
- `MatchFoundPubSubPublisherTest`
  - Redis Pub/Sub channel publish 호출을 검증합니다.
  - publish 실패가 외부로 전파되지 않고 실패 메트릭으로 격리되는지 검증합니다.
- `MatchFoundPubSubSubscriberTest`
  - Pub/Sub 메시지 수신 후 decode와 dispatcher 위임을 검증합니다.
  - decode 실패와 dispatch 실패가 listener 밖으로 전파되지 않고 실패 메트릭으로 기록되는지 검증합니다.
- `MatchFoundNotificationDispatcherTest`
  - 두 유저 모두 현재 인스턴스에 연결된 경우 두 유저에게 전송되는지 검증합니다.
  - 한 유저만 연결된 경우 연결된 유저에게만 전송되는지 검증합니다.
  - 두 유저 모두 미연결이어도 예외 없이 종료되는지 검증합니다.
  - 로컬 연결 조회 hit/miss 메트릭이 증가하는지 검증합니다.
- `MatchNotificationPubSubConfigTest`
  - `notification:match_found` channel을 구독하는 Redis listener container 생성을 검증합니다.
- `SseNotificationMetricsTest`
  - Pub/Sub publish, subscribe, dispatch hit/miss 메트릭 기록을 검증합니다.
- 로컬 직접 전송 리스너를 제거했기 때문에 `MatchFoundEvent`가 직접 SSE 전송과 Pub/Sub 전송을 동시에 수행하지 않습니다.

- [ ] **부하 테스트 재실행**
  - 2대 API 인스턴스 기준 `MODE=match`, `CONNECTIONS=10000`, `JOIN_TPS=50` 재실행
  - 기대 결과
    - 10,000 SSE 연결 성공률 99% 이상
    - `match_found` 서버 전송량이 목표 50 events/s에 근접
    - 인스턴스별 활성 SSE 연결 수와 전송 수가 균형 있게 분산
    - `send_failure`, `error`, `timeout` 급증 없음

#### 구현 방향

현재 프로젝트는 API 인스턴스가 2대 올라가는 구조이고, Redis는 이미 공통 인프라로 사용 중입니다.

따라서 V1.1에서는 Redis Pub/Sub을 사용해 `match_found` 알림 이벤트를 모든 API 인스턴스에 전파합니다.

```text
MatchFoundService
-> Spring MatchFoundEvent 발행
-> MatchFoundEventPublisher가 Redis Pub/Sub publish
-> 모든 API 인스턴스의 subscriber가 메시지 수신
-> 각 인스턴스는 자기 SseConnectionRegistry에서 연결된 유저만 찾아 SSE 전송
```

이 구조는 메시지 영속성을 보장하지 않습니다. 다만 이번 요구사항은 "현재 SSE로 연결되어 있는 유저에게 즉시 match_found 알림을 전달"하는 것이므로 Pub/Sub이 적합합니다.

재전송 보장, 장애 복구, 오프라인 유저 알림까지 필요해지면 Redis Stream 또는 별도 알림 저장소를 후속 개선으로 검토합니다.

### 11. 후속 이슈 분리
- [ ] **수락/거절 API 후속 이슈로 분리**
  - `POST /api/v1/match/{matchId}/accept`
  - `POST /api/v1/match/{matchId}/reject`
  - 양쪽 수락 완료 처리
  - 한쪽 거절 처리
  - 타임아웃 처리

- [ ] **수락/거절 상태 동기화 이벤트 후속 이슈로 분리**
  - `match_accept`
  - `match_reject`
  - `match_completed`
  - `match_timeout`
