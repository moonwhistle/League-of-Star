---
trigger: always_on
---

# League of Smite — Project Context

---

## 프로젝트 개요

- **서비스명**: League of Smite
- **한 줄 소개**: 강타 타이밍을 겨루는 1v1 실시간 랭킹 대전 게임
- **핵심 가치**: "prove your smite timing" — 정글러의 핵심 역량인 강타 싸움을 독립 게임으로 승화

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| **Backend** | Java 17, Spring Boot 4.0, Spring WebSocket (STOMP), Spring Security + JWT, OAuth2 Client, JPA (Hibernate), MySQL, Redis |
| **Frontend** | React 19, TypeScript, Vite, STOMP.js + SockJS, Canvas / HTML5 |
| **Infra** | Docker, Nginx, GitHub Actions |

---

## 핵심 참조 문서

코드 변경 시 정책과의 정합성을 반드시 확인할 것:

- 전체 기획: @docs/project/overallplan.md
- 비즈니스 정책: @docs/project/policy.md
- DB 설계: @docs/DB/DDL.md

---

## 프로젝트 구조

```
smite/
├── backend/                  # Gradle 루트
│   ├── smite-api/            # API 모듈 (Controller, DTO, Config, Service 구현체)
│   ├── smite-core/           # Core 모듈 (Entity, Repository, 게임 로직, Service 인터페이스)
│   └── smite-infra-redis/    # Redis 인프라 모듈 (매칭 큐, 세션 관리)
├── frontend/                 # React 19 + TypeScript + Vite
└── docs/                     # 기획, 정책, DB 설계 문서
```

### 모듈 의존성 방향

```
smite-api → smite-core
smite-api → smite-infra-redis
smite-infra-redis → smite-core
smite-core → (독립, JPA/Hibernate만 의존)
```

- **역방향 의존 금지**: smite-core는 smite-api, smite-infra-redis에 절대 의존하지 않는다.
- **smite-core는 순수 도메인**: Spring Web, Redis 등 인프라 기술에 의존하지 않는다.

---

## 작업 정리 파일

- **경로**: `/docs/antigravity/backend/` 또는 `/docs/antigravity/frontend/`
- **파일명**: 이슈 번호 기반 (예: `issue-42-add-matching-queue.md`)
- **형식**: 한 줄 요약은 영어, 세부 내용은 한글
- **1 이슈 = 1 파일**: 하나의 PR 작업을 하나의 파일로 관리
- **지속 갱신**: 코드 변경이 있을 때마다 해당 파일을 업데이트

---

## 핵심 도메인

| 도메인 | 책임 |
|--------|------|
| **Auth** | 회원가입, 로그인 (이메일 + 소셜), JWT 발급/검증 |
| **User** | 유저 프로필, 티어/LP 관리 |
| **Match** | 매칭 큐 관리, 티어 기반 상대 탐색, 매칭 수락 처리 |
| **Game** | 시나리오 생성, WebSocket 통신, Rewind 판정, 결과 처리 |
| **Ranking** | 랭킹 조회, 리더보드 |
| **Record** | 전적 기록, 통계 |
