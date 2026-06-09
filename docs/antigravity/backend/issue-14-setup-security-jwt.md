# Issue-14 — Setup Spring Security & JWT Authentication [COMPLETED]

## 📌 Feature Description
`league-of-star-api` 모듈에 실시간 경쟁 게임의 기반이 되는 보안 및 인증 체계를 구축함. 유저 식별(userId)의 근거가 되는 JWT 토큰 발급 및 검증 로직을 구현하고, API 엔드포인트별 접근 권한을 설정함.

## 📚 Tasks

- [x] **보안 의존성 추가**: `spring-boot-starter-security` 및 JWT 라이브러리(jjwt) 반영
- [x] **Security Config 구현**: FilterChain 설정, Stateless 세션 정책 수립
- [x] **JWT Provider 구현**: Access Token 생성, 유효성 검증, Claims(userId, email) 추출 로직 개발
- [x] **JWT Authentication Filter 구현**: HTTP Header에서 토큰 추출 및 SecurityContext 등록 필터 체계 구축
- [x] **커스텀 아규먼트 리졸버 (@AuthUser)**: 컨트롤러에서 유저 ID를 즉시 주입받기 위한 편의 기능 구현
- [x] **최종 검증**: JwtTokenProvider 단위 테스트 100% 통과

---

## 📝 Work Summary
- **의도**: 도메인 간 간접 참조를 원활하게 지원하기 위해 모든 요청에서 `userId`를 쉽고 안전하게 추출할 수 있는 기반 마련.
- **결과**: `jjwt 0.12.5` 기반의 인증 엔진 구축 완료. 컨트롤러에서 `@AuthUser Long userId` 파라미터 사용 가능.
- **검증**: `JwtTokenProviderTest`를 통해 토큰 생성 및 파싱 정합성 확인 완료.
