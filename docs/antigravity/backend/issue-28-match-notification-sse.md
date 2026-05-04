# Issue-28: 매칭 알림 기능 구현 (Netty 기반 SSE)

## 📌 Feature Description

매칭 엔진에서 매칭이 성사되었을 때, 대상 유저 2명에게 실시간으로 매칭 성사 알림을 전달하는 SSE(Server-Sent Events) 기반 알림 기능을 구현합니다.

이번 이슈의 목표는 **매칭 성사 이벤트를 클라이언트까지 안정적으로 전달하는 실시간 이벤트 스트림 구조를 만드는 것**입니다. 클라이언트는 매칭 대기 화면에서 SSE 연결을 열고, 서버는 `MatchFoundEvent`가 발생하면 해당 유저의 SSE 연결로 `match_found` 이벤트를 전송합니다.

수락/거절 API, 양쪽 수락 완료 처리, 거절/타임아웃 상태 동기화는 이번 이슈에서 구현하지 않습니다. 해당 기능은 후속 이슈인 `[FEAT] 매칭 수락/거절 API 및 상태 동기화 구현`에서 처리합니다.

---

## 📌 Summary

매칭 알림은 서버에서 클라이언트로 전달되는 단방향 이벤트입니다. 클라이언트가 서버에 지속적으로 입력을 보내는 구조가 아니므로, WebSocket보다 SSE가 현재 요구사항에 더 적합합니다.

SSE는 HTTP 기반이라 인증, 로깅, 장애 분석이 단순하고, 브라우저의 `EventSource`가 자동 재연결을 지원합니다. 매칭 성사처럼 서버가 특정 순간에 이벤트를 push해야 하는 기능에 잘 맞습니다.

이번 이슈에서는 API 모듈에 SSE 연결 API를 추가하고, 유저별 SSE 연결을 관리하는 컴포넌트를 구성합니다. 이후 매칭 모듈에서 발행하는 `MatchFoundEvent`를 API 모듈에서 구독하여 대상 유저 2명에게 매칭 성사 알림을 전송합니다.

Netty 기반 SSE를 사용하기 위해 WebFlux/Netty 적용 범위를 먼저 검토합니다. 현재 API 모듈은 `spring-boot-starter-web` 기반이므로, 기존 MVC API와 충돌 없이 SSE 스트림을 구성할 수 있는 방식으로 설계합니다.

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

## 📚 Changes

- API 모듈에 SSE 연결 엔드포인트를 추가합니다.
  - 예: `GET /api/v1/match/notifications/stream`
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

- Netty 기반 SSE 구성을 검토하고 적용합니다.
  - WebFlux/Netty 의존성 추가 여부를 검토합니다.
  - 기존 MVC API와 함께 사용할 때 애플리케이션 타입, 포트, 필터/보안 설정 충돌 여부를 확인합니다.
  - 필요하면 SSE 전용 라우터/핸들러와 기존 MVC 컨트롤러의 경계를 분리합니다.

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
    participant Listener as MatchFoundEventListener
    participant Redis as Redis

    ClientA->>API: GET /api/v1/match/notifications/stream
    API->>API: JWT 인증 후 userA 식별
    API->>Registry: userA SSE 연결 등록
    API-->>ClientA: event: connected

    ClientB->>API: GET /api/v1/match/notifications/stream
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

    API->>Listener: MatchFoundEvent 수신
    Listener->>Registry: userA SSE 연결 조회
    Listener->>Registry: userB SSE 연결 조회
    Listener-->>ClientA: event: match_found
    Listener-->>ClientB: event: match_found

    alt 특정 유저 SSE 연결 없음
        Listener->>Listener: 전송 스킵 및 로그 기록
    else 이벤트 전송 실패
        Listener->>Registry: 실패한 연결 제거
        Listener->>Listener: 실패 로그 기록
    end
```

### 흐름 요약

1. 클라이언트는 매칭 대기 화면에 진입하면 SSE 스트림을 먼저 연결합니다.
2. 서버는 인증된 유저 ID를 기준으로 SSE 연결을 저장합니다.
3. 매칭 엔진이 두 유저를 원자적으로 큐에서 제거합니다.
4. `MatchFoundService`가 유저 상태와 매칭 세션을 Redis에 저장합니다.
5. `MatchFoundEvent`가 발행됩니다.
6. API 모듈의 이벤트 리스너가 이벤트를 받아 두 유저의 SSE 연결을 조회합니다.
7. 연결이 살아 있으면 `match_found` 이벤트를 전송합니다.
8. 연결이 없거나 전송에 실패하면 알림 실패만 로그로 남기고, 매칭 상태는 Redis 기준으로 유지합니다.

## 📌 Related Issue
- Closes #28

---

## 📚 Tasks

### 1. SSE 적용 방식 결정
- [ ] **현재 API 서버 구조 확인**
  - `smite-api`가 현재 `spring-boot-starter-web` 기반으로 동작하는지 확인
  - Netty 기반 SSE를 위해 WebFlux 적용이 필요한지 검토
  - 기존 MVC 컨트롤러, Spring Security, RestDocs/OpenAPI 구성과 충돌 가능성 확인

- [ ] **SSE 구현 방식 결정**
  - 선택지 A: Spring MVC `SseEmitter` 기반 구현
  - 선택지 B: Spring WebFlux + Netty 기반 `Flux<ServerSentEvent<?>>` 구현
  - 포트폴리오 목표가 "Netty 기반 SSE"이므로, 가능하면 WebFlux/Netty 기반으로 설계
  - 기존 API와 충돌이 크면 SSE 전용 설정/모듈 분리 방안 검토

### 2. SSE 연결 API 구현
- [ ] **SSE 연결 엔드포인트 추가**
  - `GET /api/v1/match/notifications/stream`
  - 인증된 유저 ID를 `@AuthUser` 또는 SecurityContext에서 추출
  - 응답 Content-Type은 `text/event-stream` 형태로 제공

- [ ] **초기 연결 이벤트 전송**
  - 연결 직후 `connected` 이벤트 전송
  - 클라이언트가 정상 연결 여부를 바로 알 수 있게 구성
  - 필요 시 현재 서버 시간 또는 connection id 포함

- [ ] **인증 실패 처리**
  - 인증되지 않은 요청은 SSE 연결을 열지 않음
  - 기존 인증 실패 응답 정책과 동일하게 처리

### 3. 유저별 SSE 연결 관리
- [ ] **SSE 연결 저장소 구현**
  - 유저 ID를 key로 활성 연결 저장
  - 저장 구조 예: `ConcurrentHashMap<Long, SseConnection>`
  - 연결 객체는 이벤트 전송, 완료 처리, 종료 처리를 캡슐화

- [ ] **재연결 처리**
  - 같은 유저가 새로 연결하면 기존 연결을 종료하고 새 연결로 교체
  - 브라우저 새로고침, 네트워크 재연결 상황에서 중복 연결이 쌓이지 않도록 처리

- [ ] **연결 제거 처리**
  - 클라이언트 연결 종료 시 저장소에서 제거
  - 타임아웃 발생 시 저장소에서 제거
  - 이벤트 전송 실패 시 저장소에서 제거

### 4. Heartbeat 구현
- [ ] **주기적 heartbeat 이벤트 전송**
  - 일정 주기로 `heartbeat` 이벤트 전송
  - 프록시, 브라우저, 네트워크 장비가 유휴 연결을 끊지 않도록 유지
  - heartbeat 주기는 설정값으로 분리

- [ ] **heartbeat 실패 연결 정리**
  - heartbeat 전송 중 예외 발생 시 해당 연결 제거
  - 실패 연결이 계속 저장소에 남아 메모리 누수가 생기지 않도록 처리

### 5. MatchFoundEvent 연동
- [ ] **매칭 성사 이벤트 리스너 구현**
  - `MatchFoundEvent`를 구독하는 listener 추가
  - 이벤트 발생 시 `userA`, `userB` 대상 연결 조회
  - 연결이 있으면 각각에게 `match_found` 이벤트 전송

- [ ] **매칭 성사 payload 정의**
  - `matchId`
  - `userId`
  - `opponentUserId`
  - `acceptTimeoutSeconds`
  - 필요 시 `eventCreatedAt`

- [ ] **미연결 유저 처리**
  - 대상 유저의 SSE 연결이 없으면 이벤트 전송은 스킵
  - 매칭 상태는 Redis 세션에 이미 저장되어 있으므로, 전송 실패만 로그로 남김
  - 재전송/보상 정책은 후속 이슈에서 검토

### 6. 전송 실패 및 로깅 처리
- [ ] **전송 실패 예외 처리**
  - 끊어진 SSE 연결에 전송 시 예외를 잡고 연결 제거
  - 매칭 성사 처리 흐름 전체가 실패하지 않도록 알림 실패를 격리

- [ ] **운영 로그 정리**
  - 연결 생성 로그
  - 연결 종료 로그
  - 매칭 성사 이벤트 전송 성공/실패 로그
  - heartbeat 실패 로그

### 7. API 문서 및 사용 예시 작성
- [ ] **SSE 연결 API 문서화**
  - 요청 URL
  - 인증 방식
  - 이벤트 이름
  - 이벤트 payload 예시

- [ ] **클라이언트 사용 예시 작성**
  - `EventSource` 연결 예시
  - `match_found` 이벤트 수신 예시
  - 연결 종료/재연결 시 주의사항

### 8. 테스트 코드 작성
- [ ] **SSE 연결 저장소 단위 테스트**
  - 연결 등록 테스트
  - 재연결 시 기존 연결 교체 테스트
  - 연결 종료 시 제거 테스트

- [ ] **매칭 성사 이벤트 전송 테스트**
  - `MatchFoundEvent` 발생 시 두 유저에게 이벤트가 전송되는지 검증
  - 한 명만 연결된 경우 연결된 유저에게만 전송되는지 검증
  - 두 명 모두 연결되지 않은 경우 예외 없이 종료되는지 검증

- [ ] **전송 실패 처리 테스트**
  - 이벤트 전송 중 예외 발생 시 연결이 제거되는지 검증
  - 전송 실패가 매칭 이벤트 처리 전체를 중단시키지 않는지 검증

- [ ] **heartbeat 테스트**
  - heartbeat 이벤트가 주기적으로 전송되는지 검증
  - heartbeat 실패 시 연결이 제거되는지 검증

### 9. 후속 이슈 분리
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
