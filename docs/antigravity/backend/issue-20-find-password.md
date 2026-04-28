# Issue-20: Implement Password Reset Feature

---

## 1. 개요 (Overview)
- **English**: Implement a password reset system using temporary tokens stored in Redis.
- **Korean**: Redis에 저장된 임시 토큰을 활용한 비밀번호 재설정 기능을 구현합니다. 이메일 인증 절차를 대신하여 비밀번호를 분실한 유저에게 보안 링크를 제공합니다.

---

## 2. 프로세스 흐름 (Process Flow)

```mermaid
sequenceDiagram
    participant User as 유저
    participant API as API Server (smite-api)
    participant Redis as Redis (smite-infra-redis)
    participant Core as Core Domain (smite-core)
    participant DB as MySQL (users table)

    Note over User, DB: [Phase 1: 재설정 요청]
    User->>API: 비밀번호 재설정 요청 (Email 입력)
    API->>Core: 유저 존재 여부 확인
    API->>API: UUID 기반 고유 토큰 생성
    API->>Redis: 토큰 저장 (Key: reset_token:{token}, Value: {email}, TTL: 10min)
    API-->>User: 재설정 링크 제공 (AuthPath 상수 기반 로그 출력)

    Note over User, DB: [Phase 2: 비밀번호 변경]
    User->>API: 새 비밀번호 제출 (Token + New Password)
    API->>Redis: 토큰 조회 및 이메일 획득
    API->>Core: 비밀번호 암호화 및 유저 정보 업데이트 요청
    Core->>DB: Password 업데이트 (BCrypt)
    API->>Redis: 사용된 토큰 삭제
    API-->>User: 비밀번호 변경 완료 메시지 (상수화된 응답)
```

---

## 3. 상세 정책 (Detailed Policy)

| 항목 | 내용 |
|------|------|
| **토큰 만료 시간** | 10분 (Redis TTL 설정) |
| **토큰 형식** | UUID (예측 불가능성 확보) |
| **이메일 발송** | 실제 발송 엔진 연동 전까지 **서버 로그 출력**으로 대체 |
| **비밀번호 저장** | **smite-api**에서 Spring Security `PasswordEncoder`로 암호화 후 전달 |
| **토큰 재사용** | 비밀번호 변경 성공 시 토큰 즉시 폐기 (1회용) |

---

## 4. 작업 목록 (Tasks)

### 4.1 Infrastructure & Core (smite-core & smite-infra-redis)
- [x] **Redis 전용 Repository 구현**: `PasswordResetTokenRepository`.
- [x] **도메인 추상화**: `PasswordResetStore` 인터페이스 정의.
- [x] **User 엔티티 업데이트**: `updatePassword` 도메인 메서드 추가.

### 4.2 Backend Application (smite-api)
- [x] **API 경로 상수화**: `AuthPath`에 비밀번호 재설정 경로 추가.
- [x] **PasswordResetService 구현**: API 모듈에서 암호화 및 Redis/DB 오케스트레이션 수행.
- [x] **Controller 구현**: `AuthPasswordController` (RestAssuredMockMvc 기반).

### 4.3 Test (계층별 검증)
- [x] **Controller Test**: `RestAssuredMockMvc`를 활용한 API 동작 검증.
- [x] **Service Test**: `Mockito` 기반의 비즈니스 로직 단위 테스트.
- [x] **Repository Test**: `AbstractRedisRepositoryTest`를 상속받은 Redis 데이터 검증 테스트.

### 4.4 Documentation
- [x] **RestDocs 업데이트**: 테스트 코드를 기반으로 API 명세서 스니펫 생성.
- [x] **이슈 문서 최신화**: 구현 완료 사항 및 테스트 전략 반영.

---------

## 📌 Summary
본 이슈는 유저가 이메일을 통해 비밀번호를 안전하게 재설정할 수 있는 기능을 구현하는 것을 목표로 합니다. 도메인 계층의 순수성을 유지하면서 Redis를 활용한 토큰 관리와 실제 이메일 발송 인프라를 연동하였습니다.

## 📚 Changes
- **Backend (API)**
  - `PasswordResetService` 구현: UUID 기반 토큰 생성, Redis 저장(TTL 10분), 이메일 발송 오케스트레이션 수행.
  - `AuthPasswordController` 구현: 재설정 링크 요청 및 제출 API 제공. (`AuthPath` 상수 활용)
  - `JavaMailEmailService` 구현: Spring Mail 기반의 실제 이메일 발송 엔진 구축.
- **Backend (Core)**
  - `UserCommandService.updatePassword()` 구현: 암호화된 비밀번호를 전달받아 유저 정보 업데이트.
  - `EmailService` 및 `PasswordResetStore` 인터페이스 정의: 인프라 기술과의 결합도 제거.
- **Infrastructure (Redis)**
  - `PasswordResetRedisStore` 구현: `@RedisHash` 엔티티를 활용한 토큰 영속성 관리.
- **Configuration**
  - `security.yml` 도입: JWT, OAuth2, Mail 설정 등 민감 정보를 분리하여 보안성 강화.
  - `application.yml`: `spring.config.import`를 통해 외부 설정 파일 명시적 로드.

## 📝 Note
- **Security**: 이메일 존재 여부에 관계없이 동일한 성공 메시지를 반환하여 이메일 열거 공격(Email Enumeration)을 방지함.
- **Architecture**: `smite-core`는 어떠한 보안 라이브러리나 외부 기술에도 의존하지 않으며, `smite-api` 계층에서 모든 기술적 구현(Encryption, Mail)을 담당함.
- **Testing**:
  - `RestAssuredMockMvc`를 활용한 Controller 테스트 및 RestDocs 명세 생성 완료.
  - Testcontainers 기반의 실제 Redis 연동 데이터 무결성 검증 완료.
  - 비즈니스 로직 단위 테스트(Mock 활용) 100% 통과.

## 📌 Related Issue
- Closes #20
