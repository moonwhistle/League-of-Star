# League of Smite — Overall Plan

---

## 1. 서비스 개요

| 항목 | 내용 |
|------|------|
| **서비스명** | League of Smite |
| **한 줄 소개** | 강타 타이밍을 겨루는 1v1 실시간 랭킹 대전 게임 |
| **핵심 가치** | "prove your smite timing" — 정글러의 핵심 역량인 강타 싸움을 독립 게임으로 승화 |

---

## 2. 핵심 유저 플로우

```
[로그인] → [매칭 대기] → [매칭 수락] → [강타 싸움 게임] → [결과 & LP 변동 확인]
```

### 2.1 로그인
- 회원가입 / 로그인 (이메일 + 비밀번호)
- 소셜 로그인 (Google, Discord) — Phase 1에서 함께 구현
- 로그인 후 메인 로비로 이동

### 2.2 매칭
- **매칭 큐 진입**: "대전 찾기" 버튼 클릭
- **매칭 알고리즘**: 티어/디비전 기반 유사 실력 상대와 매칭 (대기 시간에 따라 ±1/±2/±4/±8 디비전 확장)
- **매칭 수락**: 매칭 성사 시 양쪽에 수락/거절 팝업 (10초 타이머)
  - 둘 다 수락 → 서버가 게임방/시나리오 생성 및 Redis 상태 전환 완료 → 게임 대기 화면 진입
  - 한 명이라도 거절/타임아웃 → 10초 안에 수락한 유저는 큐 최우선 복귀, 거절/타임아웃/미응답 유저는 큐 이탈 (거절 패널티 없음)
  - 한 명이 먼저 거절해도 상대방 팝업은 10초 동안 유지되며, 제한 시간 안에 수락하면 큐 복귀 대상이 됨
- **매칭 알림 채널**: SSE는 `match_found`와 최종 `match_response_result`까지만 담당
  - 게임방 생성 성공 시 `match_response_result.game`에 `gameRoomId`, `videoUrl`, `webSocketUrl` 포함
  - 게임방 생성 실패 시 `GAME_SETUP_FAILED` 결과를 전달하고 양쪽 모두 start 버튼 화면으로 복귀
  - 게임방 생성 후 Redis 상태 전환 실패 시 생성된 게임방/참여자는 `ABORTED`로 보상 처리하고 동일하게 `GAME_SETUP_FAILED` 결과를 전달
  - 게임방 생성 실패 또는 Redis 상태 전환 실패 시 매칭 큐에 자동 복귀하지 않음
  - 클라이언트는 `match_response_result` 수신 후 매칭 SSE `EventSource.close()`를 호출
  - 게임 대기 화면 진입 이후 준비/RTT/카운트다운/게임 시작/입력/종료는 WebSocket 담당

### 2.3 강타 싸움 게임
- 두 플레이어가 **동일한 드래곤의 HP 바**를 실시간으로 공유
- 드래곤 HP가 **불규칙하게 감소** (서버에서 사전 생성한 시나리오 기반)
- MP4 배경은 gameRoom별로 만들지 않고 공통 static resource를 사용
- 드래곤 위에 **마우스를 올린 상태**에서 **D 또는 F 키**를 눌러 강타 발동
- 각 플레이어는 **단 한 번** 강타 사용 가능
- 드래곤 HP가 0에 도달하면 **즉시 게임 종료**
- **서버 권위 판정 시스템**으로 승패 결정 (상세: 3장)

### 2.4 결과 확인
- 승리 / 패배 / 무승부 표시
- LP 변동량 표시 (+/- LP)
- 승급전 진입/결과 표시
- 티어 승급/강등 시 연출
- 전적 기록 저장

---

## 3. 게임 메카닉 — Rewind 판정 시스템

### 3.1 시나리오 기반 HP 감소

서버는 게임 시작 전 **HP 감소 시나리오**를 사전 생성합니다.

```
시나리오 = [(t₀, hp₀), (t₁, hp₁), (t₂, hp₂), ..., (tₙ, 0)]
```

#### 드래곤 기본 사양

| 항목 | 값 |
|------|----|
| **몬스터** | 장로 드래곤 (Elder Dragon) — 추후 바론, 전령 등 확장 가능한 구조로 설계 |
| **초기 HP** | 10,000 |
| **강타 데미지** | 1,200 (킬존: HP ≤ 1200) |
| **게임 제한 시간** | 8 ~ 17초 (매판 랜덤, 시나리오 생성 시 결정) |

#### HP 감소 패턴: 랜덤 버스트 (Random Burst)

챔피언들이 스킬/평타를 섞어 때리는 실제 LoL의 느낌을 시뮬레이션합니다.

```
HP: ████░██████░░█░░████░░█░░░░░░░
     팀원 스킬  평타  궁극기  평타
```

- HP가 **불규칙한 덩어리(버스트)** 단위로 감소
- 매판 서버가 새로운 랜덤 시나리오를 생성 → 킬존 진입 시점이 매번 다름
- HP 바를 읽는 **판독력** + 순간적인 클릭 **반응속도** 둘 다 필요
- 양쪽 클라이언트에는 게임 시작 직전 동일한 시나리오를 전달 (WebSocket)
- 클라이언트는 서버가 내려준 `startAt` 기준으로 MP4 재생과 HP overlay를 동기화

### 3.2 판정 프로세스 (서버 권위 방식)

클라이언트는 시간 정보를 전송하지 않습니다. 서버가 직접 수신 시각을 기록하여 조작을 원천 차단합니다.

```
1. 유저: 드래곤 위에 마우스 올림 + 키(D/F) 입력
2. 클라이언트 → 서버: WebSocket으로 "SMITE" 액션만 전송 (시간 정보 없음)
3. 서버: 수신 시각 직접 기록 (server_receive_time)
4. 서버: smite_time = (server_receive_time - game_start_time) - RTT / 2
5. 서버: 시나리오에서 smite_time 시점의 HP 역산
6. HP ≤ 1200 → 킬 성공 (Smite Secured)
7. HP > 1200 → 킬 실패 (Smite Failed)
```

### 3.3 승패 판정

| 상황 | 결과 |
|------|------|
| 한 명만 킬 성공 | 킬 성공한 플레이어 **승리** |
| 둘 다 킬 성공 | **먼저 누른 사람** 승리 (선착순 — 먼저 강타를 성공시킨 시점에 드래곤 즉사) |
| 둘 다 킬 실패 (드래곤이 자연사) | **무승부** |

> **강타 데미지**: 1200 고정 (True Damage)

### 3.4 RTT 보정

- 게임 시작 세팅 화면에서 5회 Ping-Pong 측정, **중간값(Median)** 사용
- 서버는 `smite_time = (server_receive_time - game_start_time) - RTT/2` 방식으로 보정
- RTT 2000ms 초과 시 게임 진입 차단 (안정적 환경에서 재시도 유도)

---

## 4. 티어 & LP 시스템

### 4.1 티어 체계 (LoL 공식 기준)

| 등급 | 디비전 | 비고 |
|------|--------|------|
| **Iron** | IV → I | 최하위 |
| **Bronze** | IV → I | |
| **Silver** | IV → I | |
| **Gold** | IV → I | |
| **Platinum** | IV → I | |
| **Emerald** | IV → I | |
| **Diamond** | IV → I | Diamond I → Master 승급전 적용 |
| **Master** | 없음 (LP 무제한) | Apex 티어 — LP 200 도달 시 GM 승급 |
| **Grandmaster** | 없음 (LP 무제한) | Apex 티어 — LP 500 도달 시 Challenger 승급 |
| **Challenger** | 없음 (LP 무제한) | Apex 최상위 |

### 4.2 LP 규칙

| 규칙 | 설명 |
|------|------|
| **승급전 진입** | 99 LP 또는 100 LP 도달 시 승급전 진입 |
| **승급전 방식** | **3판 2선승제 (RankSeries로 관리)** — 2승 시 성공, 2패 시 실패 |
| **승급전 적용 범위** | Iron IV ~ Diamond I (디비전 간 승급) + Diamond I → Master (최종 관문) |
| **Apex 승급** | Master → GM: LP 200+ 자동 승급 / GM → Challenger: LP 500+ 자동 승급 |
| **승급전 실패** | LP 75로 세팅 (현재 디비전 유지) |
| **강등 조건** | 0 LP에서 패배 시 이전 디비전으로 강등 |
| **LP 증감량** | 상대와의 티어 차이에 따라 유동적 (15~35 LP, 상세: 4.3) |
| **무승부** | LP 변동 없음 |
| **배치 게임** | 신규 유저는 10판 배치 후 초기 티어 배정 (RankSeries로 관리) |

### 4.3 매칭 & LP 계산 시스템

MMR 없이, **티어/디비전 기반 매칭** + **상대 티어 차이에 따른 LP 보정** 방식을 사용합니다.
1v1 게임 특성상 승패 자체가 실력을 반영하므로, 숨겨진 MMR은 불필요합니다.

#### 티어 점수 (내부 계산용)

| 티어 | IV | III | II | I |
|------|-----|------|-----|-----|
| Iron | 1 | 2 | 3 | 4 |
| Bronze | 5 | 6 | 7 | 8 |
| Silver | 9 | 10 | 11 | 12 |
| Gold | 13 | 14 | 15 | 16 |
| Platinum | 17 | 18 | 19 | 20 |
| Emerald | 21 | 22 | 23 | 24 |
| Diamond | 25 | 26 | 27 | 28 |

> Apex 티어(Master/GM/Challenger)는 디비전이 없으므로 **LP 근접도** 기반 매칭

#### LP 계산 공식

```
gap = 상대_티어점수 - 내_티어점수

승리 LP = clamp(25 + gap × 3, 15, 35)
패배 LP = clamp(25 - gap × 3, 15, 35)
```

#### 구체 예시 (내가 Gold III = 14 일 때)

| 상대 | gap | 승리 시 | 패배 시 |
|------|-----|---------|--------|
| Gold I (16) | +2 | **+31** | **-19** |
| Gold II (15) | +1 | **+28** | **-22** |
| Gold III (14) | 0 | **+25** | **-25** |
| Gold IV (13) | -1 | **+22** | **-28** |
| Silver I (12) | -2 | **+19** | **-31** |

→ 강한 상대를 이기면 보상 ↑, 약한 상대에게 지면 패널티 ↑

#### 매칭 범위

- **기본**: 같은 티어 ± 3 디비전 이내 매칭
- **대기 시간 초과 시**: 범위를 점진적으로 확장

### 4.4 배치 게임 (10판)

신규 유저는 회원가입 시 자동으로 **RankSeries(PLACEMENT)**가 생성되며, 10판의 게임 결과에 따라 아래와 같이 초기 티어가 결정됩니다.

| 배치 승수 | 배정 티어 |
|-----------|----------|
| 0~2승 | Iron IV |
| 3~4승 | Bronze IV |
| 5~6승 | Silver IV |
| 7~8승 | Gold IV |
| 9~10승 | Platinum IV |

---

## 5. 기술 스택

### 5.1 Backend

| 기술 | 용도 |
|------|------|
| **Java 17** | 메인 언어 |
| **Spring Boot 4.0** | 백엔드 프레임워크 |
| **Spring WebSocket** | 게임 WebSocket JSON 통신 |
| **Spring Security + JWT** | 인증/인가 |
| **Spring OAuth2 Client** | 소셜 로그인 (Google, Discord) |
| **JPA (Hibernate)** | ORM |
| **MySQL** | 메인 DB |
| **Redis** | 매칭 큐, 세션 관리, 실시간 데이터 캐싱 |

### 5.2 Frontend

| 기술 | 용도 |
|------|------|
| **React 19** | UI 프레임워크 (로비, HUD, 상태 관리) |
| **TypeScript** | 타입 안전성 |
| **Vite** | 빌드 도구 |
| **React Router** | 화면 라우팅 |
| **TanStack Query** | 서버 상태/REST API 캐싱 |
| **Native EventSource** | 매칭 SSE 수신 |
| **Native WebSocket** | 게임 준비, RTT, 카운트다운, SMITE 입력 |
| **HTML video + React/CSS overlay** | MP4 배경 재생, HP bar/HUD 렌더링 |
| **Vitest + React Testing Library** | 프론트엔드 테스트 |

### 5.3 Infra *(확장 시)*

| 기술 | 용도 |
|------|------|
| **Docker** | 컨테이너화 |
| **Nginx** | 리버스 프록시, 정적 파일 서빙 |
| **GitHub Actions** | CI/CD (이미 구축됨) |

---

## 6. 멀티모듈 구조

```
smite/
├── smite-api/              # API 모듈
│   ├── controller/         # REST Controller, WebSocket Handler
│   ├── dto/                # Request/Response DTO
│   ├── config/             # Security, WebSocket, OAuth2 설정
│   └── service/            # 서비스 구현체 (Auth, Match, Game, Ranking)
│
├── smite-core/             # Core 모듈 (도메인 + 게임 로직)
│   ├── domain/             # 엔티티 (User, Match, GameRecord, Tier...)
│   ├── repository/         # JPA Repository 인터페이스
│   ├── game/               # 게임 판정 로직 (시나리오 생성, Rewind 판정)
│   └── service/            # 서비스 인터페이스
│
├── smite-infra-redis/      # Redis 인프라 모듈
│   ├── config/             # Redis 설정
│   ├── matching/           # 매칭 큐 구현 (Redis Sorted Set 등)
│   └── session/            # 세션/캐시 관리
│
├── docs/                   # 문서
├── build.gradle            # 루트 빌드 설정
└── settings.gradle         # 모듈 등록
```

### 모듈 의존성

```
smite-api → smite-core, smite-infra-redis
smite-infra-redis → smite-core
smite-core → (독립, JPA/Hibernate만 의존)
```

| 모듈 | 책임 | 주요 의존성 |
|------|------|------------|
| **smite-api** | 컨트롤러, WebSocket, 보안 설정, 서비스 조합 | Spring Web, Security, WebSocket |
| **smite-core** | 도메인 엔티티, 게임 판정 로직, Repository | JPA, 순수 Java |
| **smite-infra-redis** | 매칭 큐, 세션 관리, 캐시 | Spring Data Redis |

---

## 7. 시스템 아키텍처 (High-Level)

```
┌─────────────────────────────────────────────────────────┐
│                      Client (React)                     │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────────┐ │
│  │  로그인   │  │  로비     │  │ 게임 (Video + Overlay)│ │
│  └──────────┘  └──────────┘  └────────────────────────┘ │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP (REST) + SSE + WebSocket JSON
┌──────────────────────▼──────────────────────────────────┐
│                   Spring Boot Server                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │ Auth API │  │ Match    │  │ Game     │  │ Ranking │ │
│  │ (JWT +   │  │ Service  │  │ Engine   │  │ Service │ │
│  │  OAuth2) │  │          │  │          │  │         │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│                       │              │                   │
│              ┌────────▼──┐    ┌──────▼──────┐           │
│              │   Redis   │    │    MySQL    │           │
│              │ (매칭 큐,  │    │ (유저, 전적, │           │
│              │  세션)     │    │  랭킹)      │           │
│              └───────────┘    └─────────────┘           │
└─────────────────────────────────────────────────────────┘
```

---

## 8. 핵심 도메인

| 도메인 | 책임 |
|--------|------|
| **Auth** | 회원가입, 로그인 (이메일 + 소셜), JWT 발급/검증 |
| **User** | 유저 프로필, 티어/LP 관리 |
| **Match** | 매칭 큐 관리, 티어 기반 상대 탐색, 매칭 수락 처리 |
| **Game** | 시나리오 생성, WebSocket 통신, Rewind 판정, 결과 처리 |
| **Ranking** | 랭킹 조회, 리더보드 |
| **Record** | 전적 기록, 통계 |

---

## 9. 프론트엔드 아키텍처 전략

MVP에서는 구현 단순성과 판정 정합성을 우선합니다.
렌더링 엔진을 별도로 도입하지 않고 브라우저 기본 기능과 React 상태만으로 게임 화면을 구성합니다.

### 9.1 MVP 렌더링 방식

- MP4 배경은 HTML `<video>`로 재생합니다.
- HP bar, countdown, result HUD는 React 컴포넌트와 CSS overlay로 렌더링합니다.
- HP overlay는 서버가 내려준 `startAt`과 scenario를 기준으로 `requestAnimationFrame`에서 계산합니다.
- PixiJS, Web Worker, OffscreenCanvas는 MVP 이후 성능 문제가 확인될 때 검토합니다.

### 9.2 시나리오 기반 예측 (Client-Side Prediction)
- 게임 시작 시 서버로부터 HP 감소 시나리오를 전체 수신합니다.
- 클라이언트는 `game_start_time`과 현재 시간을 대조하여 HP를 로컬에서 즉시 계산하여 렌더링합니다.
- 이 방식은 네트워크 지연이 발생하더라도 HP 바가 끊기지 않고 부드럽게 움직이게 하며, 유저의 입력 시점만 서버로 전송하여 판정받는 구조로 공정성을 극대화합니다.

### 9.3 통신 방식

- 매칭 알림은 브라우저 기본 `EventSource`로 수신합니다.
- 게임방 대기/RTT/카운트다운/SMITE/종료는 native `WebSocket`으로 JSON 메시지를 주고받습니다.
- STOMP.js, SockJS fallback은 MVP에서 사용하지 않습니다.
  RTT 측정과 SMITE 입력 경로를 단순하고 일관되게 유지하기 위해 WebSocket 단일 경로를 사용합니다.

---

## 10. 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-04-17 | 초안 작성 및 전체 기획 확정 |
| 2026-04-24 | 프론트엔드 기술 스택 고도화 (PixiJS, Web Worker 도입) |
| 2026-04-27 | 통합 시리즈 아키텍처(RankSeries) 도입 및 도메인 정규화 |
| 2026-05-13 | 매칭 SSE는 `match_response_result`까지, 게임 준비/RTT/카운트다운/SMITE/종료는 WebSocket으로 처리하는 흐름 반영. gameRoom 생성 실패 시 `GAME_SETUP_FAILED` 실패 정책 추가 |
| 2026-05-13 | MVP 프론트엔드 기술 스택을 React/TypeScript/Vite, EventSource, native WebSocket, HTML video + React/CSS overlay로 단순화. PixiJS/Web Worker/OffscreenCanvas/STOMP/SockJS는 MVP 이후 검토로 이동 |
| 2026-05-13 | gameRoom 생성 실패 시 자동 큐 복귀하지 않고 `GAME_SETUP_FAILED` reason 기준으로 start 버튼 화면 복귀하도록 정책 조정 |
| 2026-05-13 | Redis 상태 전환 실패 시 gameRoom/participant `ABORTED` 보상 처리 정책과 8~17초 게임 시간 반영 |
| 2026-05-14 | `match_response_result` 수신 후 클라이언트가 매칭 SSE `EventSource.close()`를 호출하는 책임 명시 |
