---
trigger: glob
globs: ["*.java"]
---

# Backend Convention (Java / Spring Boot)

> **활성화**: Glob — `*.java`

---

## 1. 기본

- **Java 17** (record, sealed class, pattern matching 활용)
- **Spring Boot 4.0** + **JPA (Hibernate)** + **MySQL**
- 명시되지 않은 기술 추가 금지 (필요 시 질문)
- dry + srp + magicString 제거 + 불필요한 경로 제거 후 임포트 + 안쓰는 임포트 제거 + build test 진행

---

## 2. Layered Architecture

### 2.1 계층 구조

```
controller/     ← 요청 수신, 검증, 응답 반환
service/        ← 트랜잭션 관리, 도메인 로직 실행
repository/     ← DB 접근
domain/         ← 엔티티, 비즈니스 로직 응집
```

### 2.2 계층별 책임

| 계층 | 책임 | 금지 사항 |
|------|------|----------|
| **Controller** | 요청 검증(Validation), 서비스 호출, 응답 반환 | 비즈니스 로직 포함 금지 |
| **Service** | 트랜잭션 관리(@Transactional), 도메인 로직 실행/오케스트레이션 | 직접 DB 접근 금지 (Repository를 통해서만) |
| **Repository** | DB 접근 로직 (JPA, QueryDSL 등) | 비즈니스 판단 금지 |
| **Domain** | 핵심 비즈니스 로직, 상태 변경 메서드, 도메인 규칙 검증 | 다른 계층에 의존 금지 |

### 2.3 의존성 방향 (핵심)

```
Controller → Service → Repository → DB
    ↓            ↓
   DTO      Domain Entity
```

**규칙**:
- 의존성은 **위에서 아래로**만 흐른다. 역방향 의존 절대 금지.
- **Controller**는 Service만 호출한다. Repository를 직접 호출하지 않는다.
- **Service**는 Repository를 호출한다. Controller를 알지 못한다.
- **Repository**는 Entity만 안다. Service, Controller를 알지 못한다.
- **Domain Entity**는 어떤 계층에도 의존하지 않는다. 순수 Java 객체 + JPA 어노테이션만.
- **DTO**는 Controller 계층에서 정의하고, Service 경계에서 Entity ↔ DTO 변환한다.
- 같은 계층 내 Service 간 호출은 허용하되, **순환 의존은 금지**한다.

### 2.4 멀티모듈 의존성

```
smite-api        → smite-core, smite-infra-redis
smite-infra-redis → smite-core
smite-core       → (독립)
```

- **smite-core**: Entity, Repository 인터페이스, Service 인터페이스, 게임 로직. 인프라 기술(Spring Web, Redis)에 의존하지 않는다.
- **smite-api**: Controller, DTO, Service 구현체, Config.
- **smite-infra-redis**: Redis 구현체 (매칭 큐, 세션 관리).
---

## 3. Service 분리 (CQRS-lite)

조회와 명령을 분리하여 트랜잭션 최적화와 책임 명확화를 달성한다.

### XXXReadService — 조회 전용

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingReadService {
    // 조회만 수행, 상태 변경 금지
}
```

### XXXCommandService — 생성/수정/삭제

```java
@Service
@RequiredArgsConstructor
@Transactional
public class GameCommandService {
    // 상태 변경은 반드시 Domain Entity의 메서드를 통해 수행
}
```

---

## 4. Naming Conventions

### 4.1 클래스

| 종류 | 규칙 | 예시 |
|------|------|------|
| **Class** | UpperCamelCase | `GameRoomService` |
| **Interface** | UpperCamelCase, `I` 접두사 금지 | `MatchingQueue` |
| **Impl** | 접미사 `Impl` 또는 구체적 이름 | `RedisMatchingQueue` |

### 4.2 메서드 & 변수

| 종류 | 규칙 | 예시 |
|------|------|------|
| **Method** | lowerCamelCase, 동사+명사 | `findUserById()` |
| **Variable** | lowerCamelCase | `playerCount` |
| **Constant** | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |

### 4.3 DTO & Entity

| 종류 | 규칙 | 예시 |
|------|------|------|
| **Entity** | 접미사 없음, 테이블과 매핑 | `User`, `GameRoom` |
| **Request DTO** | `XXXRequest` | `MatchAcceptRequest` |
| **Response DTO** | `XXXResponse` | `GameResultResponse` |

- 축약어 지양: `cnt` → `count`, `idx` → `index`, `msg` → `message`

---

## 5. DI & Lombok

### 의존성 주입

- **생성자 주입(Constructor Injection)** 필수.
- `@Autowired` 필드 주입 금지.
- `@RequiredArgsConstructor` 사용 권장.

### Lombok 규칙

| 어노테이션 | 사용 |
|------------|------|
| `@Getter` | ✅ 허용 |
| `@Builder` | ✅ 생성자 파라미터 3개 이상 시 권장 |
| `@ToString` | ✅ 허용 (민감정보 필드 exclude) |
| `@RequiredArgsConstructor` | ✅ 권장 |
| `@Data` | ❌ 금지 |
| `@Setter` | ❌ Entity에서 절대 금지 |

### 불변성

- 모든 필드는 가능한 `private final` 선언.
- DTO에 Java `record` 타입 적극 활용.
- Entity 상태 변경은 **명확한 의도를 가진 메서드**로만:
  ```java
  // ❌ user.setStatus("WITHDRAWN");
  // ✅ user.requestWithdrawal();
  ```

---

## 6. JPA / MySQL 규칙

- Entity에 `@Table(name = "…")` 명시.
- 연관관계 매핑 시 `fetch = FetchType.LAZY` 기본.
- `@OneToMany`의 N+1 문제를 항상 의식하고, `@EntityGraph` 또는 `fetch join`으로 해결.
- `@Enumerated(EnumType.STRING)` 사용 (ORDINAL 금지).
- `created_at`, `updated_at`은 `@MappedSuperclass`로 공통화.

---

## 7. 예외 처리

- 비즈니스 예외: `RuntimeException`을 상속한 **커스텀 예외** 사용.
- 예외는 `@RestControllerAdvice` **GlobalExceptionHandler**에서 일관 처리.
- `try-catch`로 예외를 삼키는 행위 금지.
- Early Return으로 들여쓰기 깊이(Depth) 최소화.

---

## 8. 외부 API 호출 (OAuth2 등)

- **WebClient** 사용 (RestTemplate 지양).
- 인터페이스 + 구현체 구조:
  - `OAuthApiRequester` (인터페이스)
  - `GoogleOAuthApiRequester` (구현체)
- 외부 호출은 **트랜잭션에 포함시키지 않는다.** 필요 시 Facade로 분리.

---

## 9. 테스트

- **JUnit 5 + AssertJ** 표준.
- `@DisplayName` 한글 테스트명: `@DisplayName("강타 성공 시 킬로 판정된다")`
- `given` → `when` → `then` 패턴 준수.
- 한 테스트 = 한 행위.
- Fixture(테스트 데이터)는 별도 분리.