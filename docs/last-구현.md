# Last 구현 정리

## 현재 완료된 MVP Core

로그인 이후 매칭, 게임 대기, 게임 시작, 플레이, LIGHTNING, 결과 전환, 결과 Summary까지 이어지는 핵심 게임 흐름은 구현되어 있다.

완료된 범위:

- [x] 로그인 API 연동
- [x] 인증 route guard
- [x] 매칭 join/leave
- [x] 매칭 수락/거절 command
- [x] Match SSE 수신
- [x] Game Waiting WebSocket
- [x] Game Start handoff
- [x] Three.js 기반 Game Play 화면
- [x] LIGHTNING 입력 / cooldown / HP 반영
- [x] Game Result WebSocket 전환
- [x] Game Result Summary API 조회
- [x] `League of Star` 네이밍 정합성
- [x] `/match` 인증 route 정책 복구
- [x] `front-plan.md` 핵심 완료 상태 정리

남은 작업은 완료된 MVP Core를 다시 건드리는 작업이 아니라, 화면에 이미 노출된 미완성 기능과 제품 완성도에 필요한 부가 흐름을 닫는 작업이다.

## 남은 작업 관리

- [ ] Section 1. User / Auth
- [ ] Section 2. Match Page 실데이터 전환
- [ ] Section 3. Record / Profile Page
- [ ] Section 4. Optional Game Modes
- [ ] Section 5. Deferred Account Features

## Section 1. User / Auth

### 1-1. [x] 회원가입 페이지 구현

우선순위: P1

목표:

- [x] 사용자가 회원가입 후 로그인 페이지로 이동할 수 있게 한다.
- [x] 현재 `LoginPage`에 보이는 회원가입 버튼을 실제 route와 API 흐름에 연결한다.

Backend:

- [x] 회원가입 API 계약을 확정하거나 구현한다.
- [x] endpoint를 확정한다.
  - `POST /api/v1/auth/signUp`
- [x] request shape를 확정한다.
  - `email`
  - `password`
  - `nickname`
- [x] response 정책을 확정한다.
  - 추천: 가입 성공은 command ack로 처리하고 `/login`으로 이동한다.
  - 가입 성공 즉시 token 발급 후 `/match` 이동은 후순위로 둔다.
- [x] ErrorResponse를 문서화한다.
  - 이메일 중복
  - 닉네임 중복
  - password 정책 위반
  - validation 실패

Frontend:

- [x] `/signup` route를 추가한다.
- [x] `SignupPage`를 구현한다.
- [x] `authService.signup()`을 추가한다.
- [x] `LoginPage`의 회원가입 버튼을 `/signup`으로 연결한다.
- [x] 성공 시 `/login` 이동과 성공 안내를 처리한다.
- [x] 실패 시 백엔드 `ErrorResponse.message`를 표시한다.

Policy:

- [x] 회원가입 성공은 로그인 상태로 간주하지 않는다.
- [x] access/refresh token 저장은 login response에서만 수행한다.
- [x] 새 패키지는 추가하지 않는다.

Acceptance Criteria:

- [x] 회원가입 성공 시 `/login`으로 이동한다.
- [x] 중복 이메일/닉네임 오류가 사용자에게 표시된다.
- [x] 필수 입력 validation이 동작한다.
- [x] 기존 로그인/매칭 flow가 깨지지 않는다.

### 1-2. [x] 로그아웃 구현

우선순위: P1

목표:

- [x] MatchPage 상단 로그아웃 버튼을 실제 동작으로 연결한다.

Backend:

- [x] logout API가 필요한지 결정한다.
- [x] 서버에서 refresh token revoke를 관리한다면 endpoint를 확정한다.
  - `POST /api/v1/auth/logout`
- [x] 서버 revoke 실패와 무관하게 프론트 local token clear를 수행한다고 문서화한다.

Frontend:

- [x] MatchPage logout 버튼에 handler를 연결한다.
- [x] `clearAuthTokens()` 호출 후 `/login`으로 이동한다.
- [x] 매칭 중 로그아웃 허용 정책을 정한다.
  - 추천: queued 상태에서는 먼저 leave 후 logout.
  - 게임 대기/플레이 중 logout은 route guard가 아니라 게임 이탈 정책 이슈에서 다룬다.

Policy:

- [x] 로그아웃은 인증 세션 종료 기능으로 둔다.
- [x] 게임 진행 중 정상 종료/패배/이탈 정산 정책과 섞지 않는다.

Acceptance Criteria:

- [x] 로그아웃 클릭 시 token이 제거된다.
- [x] 로그아웃 후 `/match` 접근 시 `/login`으로 이동한다.
- [x] queued 상태에서 로그아웃 정책이 문서화되어 있다.

### 1-3. [x] Token Refresh 구현

우선순위: P3

목표:

- [x] access token 만료 시 refresh token으로 세션을 연장한다.

Backend:

- [x] refresh API 계약을 확정한다.
- [x] 후보 endpoint를 확정한다.
  - `POST /api/v1/auth/refresh`
- [x] refresh token 전달 방식을 결정한다.
- [x] 새 access token만 반환할지, refresh token rotation까지 할지 결정한다.
- [x] 401 ErrorResponse를 HTTP API refresh 트리거로 정리한다.

Frontend:

- [x] `apiClient`에 401 refresh/retry 흐름을 추가한다.
- [x] refresh 성공 시 원 요청을 1회 재시도한다.
- [x] refresh 실패 시 token clear 후 `/login`으로 이동한다.
- [x] 동시 401 요청이 여러 개 발생할 때 refresh 요청을 중복으로 보내지 않게 한다.

Policy:

- [x] refresh/retry는 HTTP API에만 적용한다.
- [x] SSE/WebSocket은 기존 연결 정책을 유지하고, token 만료 시 재연결 정책을 별도 이슈에서 판단한다.
- [x] 무한 retry를 막기 위해 원 요청 재시도는 1회로 제한한다.

Acceptance Criteria:

- [x] access token 만료 후 refresh 성공 시 기존 API 요청이 성공한다.
- [x] refresh 실패 시 인증 정보가 제거되고 `/login`으로 이동한다.
- [x] refresh 중복 호출이 발생하지 않는다.

## Section 2. Match Page 실데이터 전환

### 2-1. [ ] 내 프로필 / 랭크 조회 API 계약

담당: Backend

우선순위: P1

목표:

- [ ] MatchPage가 표시할 현재 내 계정 상태의 source of truth API를 확정한다.
- [ ] 게임 결과 Summary와 현재 계정 상태 조회 책임을 분리한다.

Backend:

- [ ] endpoint를 확정한다.
  - 후보: `GET /api/v1/me/profile`
  - 후보: `GET /api/v1/users/me/rank`
- [ ] 최소 response shape를 확정한다.
  - `userId`
  - `nickname`
  - `rank`
  - `lp`
  - `tierScore`
  - `wins`
  - `losses`
  - `draws`
  - `rankUpdatedAt`
- [ ] 인증 실패, 사용자 없음, rank 미배정 ErrorResponse를 정리한다.
- [ ] RestDocs를 작성한다.

Policy:

- [ ] 이 API는 “현재 내 계정 상태”의 source of truth다.
- [ ] Game Result Summary API는 “방금 끝난 게임의 정산 결과”의 source of truth다.
- [ ] 두 응답을 서로 보정하거나 대체하지 않는다.

Acceptance Criteria:

- [ ] 프론트가 정적 nickname/rank/lp를 대체할 수 있는 payload가 확정된다.
- [ ] Authorization header 기반 인증 계약이 문서화된다.
- [ ] RestDocs와 ErrorResponse가 정리된다.

### 2-2. [ ] MatchPage 내 프로필 / 랭크 실데이터 구현

담당: Frontend

우선순위: P1

목표:

- [ ] MatchPage의 정적 사용자/랭크 표시를 2-1 API 실데이터로 교체한다.

Frontend:

- [ ] profile/rank service를 추가한다.
- [ ] MatchPage mount 시 내 프로필/랭크 정보를 조회한다.
- [ ] `Summoner`, `BRONZE IV`, `1,248 LP` 등 정적 표시를 제거한다.
- [ ] loading/error/empty 상태를 추가한다.
- [ ] 조회 실패가 매칭 시작/취소를 막지 않게 한다.

Policy:

- [ ] Authorization header는 기존 `apiClient` 정책을 따른다.
- [ ] token 만료는 issue-102 refresh/retry 정책을 따른다.
- [ ] 새 패키지는 추가하지 않는다.

Acceptance Criteria:

- [ ] API 성공 시 nickname/rank/lp/전적 요약이 표시된다.
- [ ] API 실패 시 MatchPage 진입과 매칭 버튼 동작은 유지된다.
- [ ] Game Result Summary payload를 현재 계정 상태 표시로 재사용하지 않는다.

### 2-3. [ ] 랭킹 조회 API 계약

담당: Backend

우선순위: P2

목표:

- [ ] MatchPage 왼쪽 랭킹 리스트와 요약 정보의 source of truth API를 확정한다.

Backend:

- [ ] endpoint를 확정한다.
  - 후보: `GET /api/v1/rankings?limit=...`
- [ ] response shape를 확정한다.
  - `rankPosition`
  - `userId`
  - `nickname`
  - `rank`
  - `lp`
  - `wins`
  - `losses`
  - `isCurrentUser`
- [ ] 내 순위를 목록에 포함할지 별도 필드로 내려줄지 결정한다.
- [ ] season best, top %, percentile 계산 책임을 백엔드/프론트 중 어디에 둘지 결정한다.
- [ ] RestDocs와 ErrorResponse를 정리한다.

Policy:

- [ ] 랭킹 조회는 매칭 코어 상태와 독립이다.
- [ ] 랭킹 데이터는 화면 장식이 아니라 별도 조회 도메인으로 분리한다.

Acceptance Criteria:

- [ ] MatchPage 랭킹 UI가 하드코딩 없이 그릴 수 있는 payload가 확정된다.
- [ ] pagination/limit 정책이 문서화된다.

### 2-4. [ ] MatchPage 랭킹 실데이터 구현

담당: Frontend

우선순위: P2

목표:

- [ ] MatchPage 왼쪽 랭킹 리스트와 요약 정보를 2-3 API 실데이터로 전환한다.

Frontend:

- [ ] ranking service를 추가한다.
- [ ] MatchPage 랭킹 리스트 하드코딩을 제거한다.
- [ ] 내 순위, top %, season best 표시 정책을 API 계약에 맞춘다.
- [ ] loading/error/empty 상태를 추가한다.

Policy:

- [ ] 랭킹 조회 실패는 매칭 시작/취소/SSE 흐름을 막지 않는다.
- [ ] MatchPage의 매칭 코어 상태와 랭킹 조회 상태를 섞지 않는다.

Acceptance Criteria:

- [ ] 랭킹 API 성공 시 실제 랭킹 목록이 표시된다.
- [ ] 랭킹 API 실패 시 부가 UI error만 표시된다.
- [ ] 매칭 시작/수락/게임 진입 흐름에 영향이 없다.

## Section 3. Record / Profile Page

### 3-1. [ ] 내 전적 목록 API 계약

담당: Backend

우선순위: P2

목표:

- [ ] 단일 게임 결과 Summary와 여러 게임 전적 목록의 API 책임을 분리한다.

Backend:

- [ ] endpoint를 확정한다.
  - 후보: `GET /api/v1/me/game-records`
  - 후보: `GET /api/v1/games/me/summaries`
- [ ] query 정책을 확정한다.
  - `page`
  - `size`
  - `cursor`
  - 최신순 정렬
- [ ] response shape를 확정한다.
  - `gameId`
  - `result`
  - `opponentNickname`
  - `rankBefore`
  - `rankAfter`
  - `lpChange`
  - `playedAt`
  - `reason`
- [ ] 빈 목록, 인증 실패, pagination 오류 ErrorResponse를 정리한다.
- [ ] RestDocs를 작성한다.

Policy:

- [ ] Game Result Summary는 결과 직후 상세 확인용이다.
- [ ] 전적 목록 API는 계정의 누적 기록 조회용이다.
- [ ] Summary payload를 sessionStorage에서 복원해 전적 목록처럼 사용하지 않는다.

Acceptance Criteria:

- [ ] 프론트가 최근 경기 목록과 더보기 UI를 구현할 수 있는 payload가 확정된다.
- [ ] pagination/cursor 정책이 문서화된다.

### 3-2. [ ] 전적 페이지 구현

담당: Frontend

우선순위: P2

목표:

- [ ] MatchPage의 기록 버튼을 실제 전적 조회 화면으로 연결한다.

Frontend:

- [ ] `/records` route를 추가한다.
- [ ] `RecordsPage`를 구현한다.
- [ ] MatchPage 기록 버튼을 `/records`로 연결한다.
- [ ] record service를 추가한다.
- [ ] pagination 또는 더보기 UI를 구현한다.
- [ ] loading/error/empty 상태를 구현한다.

Policy:

- [ ] 전적 목록의 source of truth는 3-1 API다.
- [ ] Game Result Summary sessionStorage를 전적 목록 source로 사용하지 않는다.
- [ ] 새 패키지는 추가하지 않는다.

Acceptance Criteria:

- [ ] 기록 버튼 클릭 시 전적 페이지로 이동한다.
- [ ] 전적 API 성공 시 최근 경기 목록이 표시된다.
- [ ] 비어 있는 경우 empty 상태가 표시된다.
- [ ] 조회 실패는 전적 화면 내부 error로 처리된다.

### 3-3. [ ] 프로필 상세 API 계약

담당: Backend

우선순위: P3

목표:

- [ ] ProfilePage가 필요한 데이터가 2-1 API로 충분한지 판단하고, 부족하면 상세 API 계약을 확정한다.

Backend:

- [ ] 2-1 profile/rank API 재사용 가능 여부를 결정한다.
- [ ] 부족하면 profile detail endpoint를 확정한다.
- [ ] 최근 전적 일부를 profile response에 포함할지 3-1 record API로 조회할지 결정한다.
- [ ] rank summary, win/loss summary, recent record summary response shape를 정리한다.
- [ ] RestDocs와 ErrorResponse를 정리한다.

Policy:

- [ ] Profile API는 계정 상태 상세 source of truth다.
- [ ] 최근 전적은 profile response에 포함하더라도 3-1 전적 목록의 축약본으로만 취급한다.

Acceptance Criteria:

- [ ] ProfilePage가 필요한 사용자 정보와 랭크 요약 payload가 확정된다.
- [ ] 최근 기록 요약을 어떤 API에서 가져올지 문서화된다.

### 3-4. [ ] 프로필 페이지 구현

담당: Frontend

우선순위: P3

목표:

- [ ] 사용자 프로필과 랭크/승패/최근 기록 요약을 별도 화면에서 확인할 수 있게 한다.

Frontend:

- [ ] `/profile` route를 추가한다.
- [ ] `ProfilePage`를 구현한다.
- [ ] 프로필 버튼 또는 avatar 영역에서 이동할 수 있게 한다.
- [ ] 내 랭크, 승패, 최근 기록 요약을 표시한다.
- [ ] profile detail API와 record summary API를 계약에 맞게 조합한다.

Policy:

- [ ] MatchPage는 매칭 시작 화면이다.
- [ ] ProfilePage는 계정 상태 상세 화면이다.
- [ ] 두 화면이 같은 API를 쓰더라도 UI state는 각 page local state로 관리한다.

Acceptance Criteria:

- [ ] 프로필 화면에서 사용자 정보와 랭크 요약을 확인할 수 있다.
- [ ] MatchPage와 동일한 source of truth 정책을 따른다.
- [ ] 기록 요약 실패가 프로필 기본 정보 표시를 막지 않는다.

## Section 4. Optional Game Modes

### 4-1. [ ] 연습 모드 API / Scenario 계약

담당: Backend

우선순위: P4

목표:

- [ ] 실제 상대 없이 LIGHTNING/게임 플레이를 테스트할 수 있는 연습 game room 계약을 확정한다.

Backend:

- [ ] 연습 게임 room 생성 API가 필요한지 결정한다.
- [ ] endpoint를 확정한다.
  - 후보: `POST /api/v1/games/practice`
- [ ] scenario 생성 정책을 확정한다.
  - 서버가 동일한 GAME_START payload shape를 내려준다.
  - 상대 user 없이도 GamePlayPage가 동작하도록 opponent nullable 정책을 정한다.
- [ ] 연습 결과를 랭크/LP/전적에 반영하지 않는 서버 정책을 문서화한다.

Policy:

- [ ] 연습 모드는 랭크/LP/전적 정산에 반영하지 않는다.
- [ ] 기존 ranked match WebSocket 계약과 섞지 않는다.

Acceptance Criteria:

- [ ] 프론트가 기존 handoff 구조로 연습 게임에 진입할 수 있는 payload가 확정된다.
- [ ] 연습 결과 미정산 정책이 문서화된다.

### 4-2. [ ] 연습 모드 프론트 진입 구현

담당: Frontend

우선순위: P4

목표:

- [ ] MatchPage 연습 모드 버튼으로 실제 연습 게임 화면에 진입한다.

Frontend:

- [ ] MatchPage 연습 모드 버튼을 4-1 API와 연결한다.
- [ ] practice 생성 응답을 기존 GameWaiting/GamePlay handoff 구조에 맞춘다.
- [ ] waiting page를 생략할지 유지할지 백엔드 payload 계약에 맞춰 구현한다.
- [ ] 연습 모드임을 UI에서 랭크 매칭과 구분한다.

Policy:

- [ ] 연습 모드 진입은 일반 match queue와 독립이다.
- [ ] LIGHTNING 입력 테스트는 가능하지만 랭크/전적 결과와 섞지 않는다.

Acceptance Criteria:

- [ ] 연습 모드 버튼으로 게임 화면에 진입할 수 있다.
- [ ] LIGHTNING 입력을 테스트할 수 있다.
- [ ] 연습 결과가 랭크/전적에 반영되지 않는다.

### 4-3. [ ] 사용자 지정 게임 API / Room 계약

담당: Backend

우선순위: P4

목표:

- [ ] 특정 사용자와 방을 만들어 게임할 수 있는 custom game 계약을 확정한다.

Backend:

- [ ] custom room 생성/초대/입장 API 계약을 확정한다.
- [ ] endpoint를 확정한다.
  - 후보: `POST /api/v1/custom-games`
  - 후보: `POST /api/v1/custom-games/{roomId}/join`
  - 후보: `POST /api/v1/custom-games/{roomId}/start`
- [ ] 초대 코드, roomId, host 권한 정책을 정한다.
- [ ] custom game result가 랭크/LP/전적에 반영되는지 결정한다.
- [ ] RestDocs와 ErrorResponse를 정리한다.

Policy:

- [ ] 사용자 지정 게임은 일반 매칭 queue와 독립이다.
- [ ] 랭크/LP 반영 여부는 별도 정책으로 명확히 둔다.

Acceptance Criteria:

- [ ] room 생성/입장/시작 payload가 확정된다.
- [ ] host 권한과 초대 코드 정책이 문서화된다.

### 4-4. [ ] 사용자 지정 게임 프론트 구현

담당: Frontend

우선순위: P4

목표:

- [ ] 사용자 지정 버튼을 실제 custom room 생성/입장 UI로 연결한다.

Frontend:

- [ ] CustomGamePage 또는 modal을 구현한다.
- [ ] room 생성, 초대 코드 복사, 입장, 시작 대기 UI를 구현한다.
- [ ] 4-3 API 계약에 맞춰 service를 추가한다.
- [ ] custom game handoff를 기존 waiting/play 구조와 연결한다.

Policy:

- [ ] 사용자 지정 게임 UI는 일반 match queue 상태와 독립이다.
- [ ] custom game result 정책은 4-3 백엔드 계약을 따른다.

Acceptance Criteria:

- [ ] 방 생성과 입장 흐름이 동작한다.
- [ ] 일반 매칭 queue 상태와 충돌하지 않는다.
- [ ] custom game result 정책이 화면 흐름에 반영된다.

## Section 5. Deferred Account Features

### 5-1. [ ] 비밀번호 찾기 API / 메일 정책

담당: Backend

우선순위: P5

Backend:

- [ ] 이메일 발송 인프라를 결정한다.
- [ ] reset token 발급/만료/사용 정책을 정한다.
- [ ] password reset API 계약을 확정한다.
- [ ] RestDocs와 ErrorResponse를 정리한다.

Policy:

- [ ] MVP 매칭/게임 흐름과 독립으로 둔다.
- [ ] 메일 발송 인프라와 보안 정책을 먼저 확정한다.

### 5-2. [ ] 비밀번호 찾기 프론트 구현

담당: Frontend

우선순위: P5

Frontend:

- [ ] 비밀번호 찾기 route/page를 구현한다.
- [ ] LoginPage의 관련 버튼과 연결한다.
- [ ] reset token 입력/검증/새 비밀번호 입력 UI를 구현한다.
- [ ] 5-1 API ErrorResponse를 사용자 메시지로 표시한다.

Policy:

- [ ] 기존 로그인/회원가입 흐름과 독립된 account recovery 흐름으로 둔다.

### 5-3. [ ] OAuth 로그인 백엔드 계약

담당: Backend

우선순위: P5

Backend:

- [ ] Google OAuth redirect/callback/session 발급 정책을 확정한다.
- [ ] 기존 email/password 계정과 OAuth 계정 연결 정책을 정한다.
- [ ] access/refresh token 발급 response가 기존 login response와 같은지 결정한다.

Policy:

- [ ] 기존 email/password login을 대체하지 않고 병렬 로그인 수단으로 둔다.

### 5-4. [ ] OAuth 로그인 프론트 구현

담당: Frontend

우선순위: P5

Frontend:

- [ ] OAuth 버튼을 실제 redirect 흐름에 연결한다.
- [ ] callback 처리 route가 필요한지 5-3 계약에 맞춰 구현한다.
- [ ] OAuth 성공 후 기존 `setAuthTokens` 저장 정책과 연결한다.

Policy:

- [ ] email/password login UI와 병렬 로그인 수단으로 제공한다.

## 추천 진행 순서

1. [x] Section 1-1. 회원가입 페이지 구현
2. [x] Section 1-2. 로그아웃 구현
3. [x] Section 1-3. Token Refresh 구현
4. [ ] Section 2-1. 내 프로필 / 랭크 조회 API 계약
5. [ ] Section 2-2. MatchPage 내 프로필 / 랭크 실데이터 구현
6. [ ] Section 2-3. 랭킹 조회 API 계약
7. [ ] Section 2-4. MatchPage 랭킹 실데이터 구현
8. [ ] Section 3-1. 내 전적 목록 API 계약
9. [ ] Section 3-2. 전적 페이지 구현
10. [ ] Section 3-3. 프로필 상세 API 계약
11. [ ] Section 3-4. 프로필 페이지 구현
12. [ ] Section 4-1/4-2. 연습 모드
13. [ ] Section 4-3/4-4. 사용자 지정 게임
14. [ ] Section 5-1/5-2. 비밀번호 찾기
15. [ ] Section 5-3/5-4. OAuth 로그인

## 판단 기준

- [x] “로그인부터 게임 결과까지”는 현재 MVP Core로 구현되어 있다.
- [x] “회원가입부터 매칭까지”라고 말하려면 Section 1-1이 필요하다.
- [ ] “MatchPage가 실제 계정 상태를 보여준다”고 말하려면 Section 2-1과 2-2가 필요하다.
- [ ] “MatchPage의 모든 주요 표시가 실데이터다”라고 말하려면 Section 2-1부터 2-4까지 필요하다.
- [ ] “내 기록을 다시 볼 수 있다”고 말하려면 Section 3-1과 3-2가 필요하다.
- [ ] “내 프로필 상세를 볼 수 있다”고 말하려면 Section 3-3과 3-4가 필요하다.
- [ ] “현재 화면의 모든 버튼이 기능한다”고 말하려면 Section 4까지 필요하다.
