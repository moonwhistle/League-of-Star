## 📌 Summary
인증(Auth) 도메인의 독립성을 확보하고 기술적 부채를 해결하기 위한 전방위적 리팩터링 및 자동화 고도화를 수행했습니다.

## 📚 Changes

### 1. 보안 인프라 및 JWT 초기 설정 (Setup)
- **보안 환경 설정**: `application-security.yml`을 통해 JWT Secret Key 및 만료 시간(TTL) 정립.
- **Spring Security 구성**: `SecurityConfig`를 통해 CSRF 비활성화, Stateless 세션 정책, 그리고 화이트리스트(`AUTH_WHITELIST`) 기반의 접근 제어를 설정했습니다.
- **편의 기능 구현**: `@AuthUser` 어노테이션과 `AuthUserArgumentResolver`를 구축하여, 컨트롤러에서 로그인 유저 정보를 한 줄로 주입받을 수 있는 인프라를 마련했습니다.

### 2. 인증 아키텍처 리팩터링 및 모듈화
- **Auth 모듈 독립**: JWT 관련 로직을 `auth` 패키지로 집약하여 도메인 응집도를 높였습니다.
- **예외 처리 표준화**: `ApiException`과 `ApiErrorCode`를 도입하여 인증 실패 사유(만료, 위조 등)를 클라이언트에게 명확히 전달하도록 개선했습니다.
- **성능 최적화**: `JwtAuthenticationFilter`에서 `ObjectMapper`를 매번 생성하지 않고 빈 주입 방식으로 변경하여 자원 효율을 높였습니다.

### 2. 리포지토리 스캔 영역 분리 (Log Clean-up)
- **JPA/Redis 격리**: JPA는 `com.sang.smite.domain`, Redis는 `com.sang.smite.infra.redis`만 스캔하도록 설정하여 서로 다른 기술 간의 인터페이스 혼선(Identificaton Error)을 제거했습니다.
- **설정 내재화**: 각 모듈이 자신의 기술 설정을 스스로 관리하도록 `RedisRepositoryConfig` 등을 모듈 내부로 이동시켜 캡슐화했습니다.

### 3. GitHub Actions 워크플로우 고도화
- **동시 레이블 처리**: 여러 레이블(ex: `feat`, `back`)을 동시에 추가할 때 워크플로우가 취소되던 버그를 수정했습니다.
- **트리거 확장**: 이슈 생성 시점에 이미 레이블이 있는 경우(`opened`)에도 브런치가 자동 생성되도록 개선했습니다.

## 📝 Note (Review Response)
리뷰 피드백에 대한 설계 의도와 조치 사항을 기록합니다.

### 1. Stateless vs Session 선택 근거
- **수평 확장성**: 서버 인스턴스 확장이 잦은 게임 서버 특성상 Stateless 방식이 유리하다고 판단했습니다.
- **로그아웃 제약**: 현재는 기초 인프라 단계이므로 별도의 무효화 처리가 없으나, 향후 서비스 고도화 시 **Redis 블랙리스트**를 도입하여 즉시 로그아웃/토큰 취소 기능을 구현할 계획입니다.

### 2. RedisRepositoriesAutoConfiguration 제외 의도
- **명확한 기술 격리**: JPA와 Redis 리포지토리 스캔 범위가 겹쳐 발생하는 로그 노이즈(`Could not safely identify...`)를 제거하고, 각 모듈이 본인의 기술 스택 영역(`domain`, `infra.redis`)만 책임지도록 선제적으로 구조화했습니다.

### 3. AuthUserArgumentResolver NPE 방지 (v1.1 보완)
- **required 속성 도입**: `@AuthUser(required = true)` 설정을 통해 인증 정보가 없을 경우 컨트롤러 진입 전 `401 Unauthorized` 예외를 던지도록 수정했습니다. 이를 통해 비인증 상황에서의 NPE 가능성을 원천 차단했습니다.

### 4. ErrorResponse 상태 코드 중복 설계
- **클라이언트 편의성**: HTTP 헤더뿐만 아니라 바디에도 상태 코드를 포함(Spring 3 `ProblemDetail` 방식)함으로써, 클라이언트 측 에러 로깅 및 파싱 시의 접근성을 높였습니다.

## 📌 Related Issue
- Closes #14
