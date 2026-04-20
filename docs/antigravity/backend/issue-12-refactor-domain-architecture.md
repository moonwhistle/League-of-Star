# Issue-12 — Refactor Domain Architecture and Modernize Mapping

## 1. Summary (What)
- 거대 객체 참조(Direct Reference)를 식별자 기반의 간접 참조(Indirect Reference)로 전환하여 도메인 간 결합도를 최소화함.
- Hibernate 6 환경에서 JSON 스냅샷 매핑 안정성을 확보하고, 비즈니스 정책을 코드(Javadoc) 및 예외 체계(CoreException)로 내재화함.

---

## 2. 세부 작업 내역 (Why & How)

### 2.1 참여자 중심의 확장 가능한 게임방 구조 도입
- **원인**: 기존 `GameRoom`에 `player1`, `player2`가 하드코딩되어 있어 관전자나 다자간 대전 확장이 불가능했음.
- **해결**: `GameParticipant` 엔티티를 도입하여 1:N 관계를 구축하고, 게임 생명주기에 따른 참여자 상태 일괄 관리 로직을 구현함.

### 2.2 간접 참조(ID 참조) 전환을 통한 모듈 격리
- **원인**: 모든 엔티티가 객체로 강하게 얽혀 있어(Direct JPA Reference) 성능 최적화가 어렵고 모듈 분리가 불가능했음.
- **해결**: 생명주기가 독립적인 애그리거트 간(예: GameRoom ↔ User)의 관계를 `Long userId` 등 식별자 참조로 변경함. 단, `GameRoom ↔ Participant` 등 핵심 애그리거트 내부는 직접 참조를 유지하여 응집도를 챙김.

### 2.3 JSON 매핑 기술 부채 해결
- **원인**: `@Embeddable`과 `@JdbcTypeCode(JSON)`이 혼용되어 하이버네이트의 리포지토리 메타데이터 생성 시 충돌(`AggregateSupportImpl` 에러)이 발생함.
- **해결**: 불필요한 `@Embeddable`을 제거하고 순수 Java `record` 타입을 활용하여 Jackson 직렬화 안정성을 확보함.

### 2.4 비즈니스 정책의 내재화
- **Javadoc 명시**: `policy.md`의 LP 산출 공식을 `Rank` VO에 직접 주석으로 기록하여 정책 파편화 방지.
- **예외 통합**: `CoreException`과 `CoreErrorCode`를 구축하여 도메인 규칙 위반 에러 처리를 표준화함.

---

## 3. 검증 (Verification)

### 3.1 자동화 테스트
- **대상**: `smite-core` 모듈 내 리포지토리 및 도메인 테스트 전수.
- **결과**: `clean :smite-core:test` 실행 결과 22개 테스트 케이스 모두 통과 (Green Bar 확보).
- **특이사항**: 간접 참조 전환에 따른 리포지토리 테스트의 객체 주입 로직을 ID 주입 방식으로 모두 수정 및 검증함.

### 3.2 문서 정합성
- **DDL.md**: 변경된 테이블 구조(ID 참조) 및 참여자 상태값(`DISCONNECTED` 등) 전수 업데이트.
- **policy.md**: 최신 리팩터링 방향에 맞춰 서버 권위 판정 및 상태 전이 로직 동기화.
