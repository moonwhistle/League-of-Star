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
- [x] 로그아웃 버튼 클릭 시 확인 모달을 표시한다.
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

### 2-1. [x] 내 프로필 조회 API 계약

담당: Backend

우선순위: P1

목표:

- [x] 로그인한 사용자의 기본 계정 정보 source of truth API를 확정한다.
- [x] 프로필 정보와 랭크/전적 상태 조회 책임을 분리한다.

Backend:

- [x] endpoint를 확정한다.
  - `GET /api/v1/users/me/profile`
- [x] 최소 response shape를 확정한다.
  - `userId`
  - `nickname`
  - `email`
  - `createdAt`
- [x] 인증 실패, 사용자 없음 ErrorResponse를 정리한다.
- [x] RestDocs를 작성한다.

Policy:

- [x] 이 API는 user/account 도메인의 source of truth다.
- [x] rank, LP, 승패, 최근 전적은 이 API에 섞지 않는다.
- [x] avatarUrl은 현재 User 엔티티에 없으므로 이번 API에 포함하지 않는다.
- [x] 닉네임/아바타 변경 같은 계정 기능이 생겨도 rank API와 독립으로 유지한다.

Acceptance Criteria:

- [x] 프론트가 정적 nickname/user 표시를 대체할 수 있는 payload가 확정된다.
- [x] Authorization header 기반 인증 계약이 문서화된다.
- [x] RestDocs와 ErrorResponse가 정리된다.

### 2-2. [x] 내 랭크 조회 API 계약

담당: Backend

우선순위: P1

목표:

- [x] 로그인한 사용자의 현재 랭크/LP/승패 상태 source of truth API를 확정한다.
- [x] 게임 결과 Summary와 현재 최종 랭크 상태 조회 책임을 분리한다.

Backend:

- [x] endpoint를 확정한다.
  - `GET /api/v1/users/me/rank`
- [x] response shape를 확정한다.
  - `userId`
  - `tier`
  - `division`
  - `rank`
  - `lp`
  - `tierScore`
  - `wins`
  - `losses`
  - `draws`
  - `rankUpdatedAt`
- [x] 인증 실패, 사용자 없음, rank 미배정 ErrorResponse를 정리한다.
- [x] RestDocs를 작성한다.

Backend field mapping:

- [x] `wins`, `losses`, `draws`는 내부 `UserRankInfo.totalWins`, `totalLosses`, `totalDraws`를 외부 API 표시명으로 매핑한다.
- [x] `rankUpdatedAt`은 별도 컬럼이 아니라 `UserRankInfo.updatedAt`을 사용한다.
- [x] `rank`는 일반 랭크에서 `GOLD_IV` 같은 `TIER_DIVISION` 문자열로 반환하고, division이 없는 Apex rank는 `MASTER`처럼 tier만 반환한다.
- [x] `RANK_NOT_FOUND`가 발생해도 rank row를 자동 생성하지 않는다.

Policy:

- [x] 이 API는 rank/season/stat 도메인의 source of truth다.
- [x] Game Result Summary API는 “방금 끝난 게임의 변화량”의 source of truth다.
- [x] Rank API는 “현재 최종 상태”만 제공한다.
- [x] Profile API의 nickname/avatar 정보를 랭크 응답에 중복하지 않는다.
- [x] API 모듈은 `UserRankInfoRepository`를 직접 참조하지 않고 core `RankReadService`를 통해 조회한다.
- [x] 단건 조회 API라 N+1은 발생하지 않는다. 후속 랭킹 리스트 API에서는 batch read service를 사용한다.

Acceptance Criteria:

- [x] 프론트가 정적 rank/lp/승패 요약을 대체할 수 있는 payload가 확정된다.
- [x] Authorization header 기반 인증 계약이 문서화된다.
- [x] RestDocs와 ErrorResponse가 정리된다.

### 2-3. [x] MatchPage 내 프로필 / 랭크 실데이터 구현

담당: Frontend

우선순위: P1

목표:

- [x] MatchPage의 정적 사용자/랭크 표시를 2-1, 2-2 API 실데이터 조합으로 교체한다.

Frontend:

- [x] profile service를 추가한다.
- [x] rank service를 추가한다.
- [x] MatchPage mount 시 profile API와 rank API를 병렬 조회한다.
- [x] `Summoner`, `BRONZE IV`, `1,248 LP` 등 정적 표시를 제거한다.
- [x] loading/error/empty 상태를 추가한다.
- [x] 조회 실패가 매칭 시작/취소를 막지 않게 한다.

Policy:

- [x] Authorization header는 기존 `apiClient` 정책을 따른다.
- [x] token 만료는 issue-102 refresh/retry 정책을 따른다.
- [x] 프로필 조회 실패와 랭크 조회 실패는 각각 독립적으로 표시한다.
- [x] 프로필 API와 랭크 API 응답을 프론트에서 보정하거나 합성 저장하지 않고 화면 표시용으로만 조합한다.
- [x] 새 패키지는 추가하지 않는다.

Acceptance Criteria:

- [x] profile API 성공 시 nickname/user 정보가 표시된다.
- [x] rank API 성공 시 rank/lp/전적 요약이 표시된다.
- [x] profile/rank 중 하나가 실패해도 MatchPage 진입과 매칭 버튼 동작은 유지된다.
- [x] Game Result Summary payload를 현재 계정 상태 표시로 재사용하지 않는다.

### 2-4. [x] 랭킹 조회 API 계약

담당: Backend

우선순위: P2

목표:

- [x] MatchPage 왼쪽 랭킹 리스트와 요약 정보의 source of truth API를 확정한다.

Backend:

- [x] endpoint를 `GET /api/v1/rankings?limit=...`로 확정한다.
- [x] `limit` query parameter 정책을 확정한다.
  - 기본값: `5`
  - 허용 범위: `1~50`
  - 범위 밖 요청: `400 COMMON_003 INVALID_INPUT`
- [x] response shape를 확정한다.
  - `summary`
    - `myRankPosition`
    - `topPercent`
    - `totalRankers`
  - `entries[]`
    - `rankPosition`
    - `userId`
    - `nickname`
    - `tier`
    - `division`
    - `rank`
    - `lp`
    - `tierScore`
    - `wins`
    - `losses`
    - `draws`
    - `isCurrentUser`
  - `currentUser`
    - `entries[]`와 같은 row shape
- [x] 내 순위는 `currentUser` 별도 필드로 내려준다.
- [x] top % 계산은 백엔드가 담당한다.
- [x] season best는 API 계약에서 제외한다.
- [x] RestDocs와 ErrorResponse를 정리한다.
- [x] nickname 조회는 row별 단건 조회가 아니라 `findByIds` batch 조회로 조립한다.
- [x] rank 정렬 기준을 백엔드 source of truth로 확정한다.
  - `tierScore desc`
  - `lp desc`
  - `wins desc`
  - `losses asc`
  - `draws desc`
  - `userId asc`

Policy:

- [x] 랭킹 조회는 매칭 코어 상태와 독립이다.
- [x] 랭킹 데이터는 화면 장식이 아니라 별도 조회 도메인으로 분리한다.
- [x] 랭킹 API 실패는 매칭 시작/수락/게임 진입 흐름을 막지 않는다.
- [x] API 모듈은 core repository를 직접 참조하지 않고 `RankReadService`, `UserReadService`를 조합한다.

Acceptance Criteria:

- [x] MatchPage 랭킹 UI가 하드코딩 없이 그릴 수 있는 payload가 확정된다.
- [x] pagination/limit 정책이 문서화된다.

### 2-5. [x] MatchPage 랭킹 실데이터 구현

담당: Frontend

우선순위: P2

목표:

- [x] MatchPage 왼쪽 랭킹 리스트와 요약 정보를 2-4 API 실데이터로 전환한다.

Frontend:

- [x] ranking service를 추가한다.
- [x] MatchPage 랭킹 리스트 하드코딩을 제거한다.
- [x] 내 순위, top % 표시 정책을 API 계약에 맞춘다.
- [x] season best UI를 제거한다.
- [x] loading/error/empty 상태를 추가한다.

Policy:

- [x] 랭킹 조회 실패는 매칭 시작/취소/SSE 흐름을 막지 않는다.
- [x] MatchPage의 매칭 코어 상태와 랭킹 조회 상태를 섞지 않는다.

Acceptance Criteria:

- [x] 랭킹 API 성공 시 실제 랭킹 목록, 내 순위, top %가 표시된다.
- [x] season best 섹션이 표시되지 않는다.
- [x] 랭킹 API 실패 시 부가 UI error만 표시된다.
- [x] 매칭 시작/수락/게임 진입 흐름에 영향이 없다.

## Section 3. Record / Profile Page

### 3-1. [x] 내 전적 목록 API 계약

담당: Backend

우선순위: P2

목표:

- [x] 단일 게임 결과 Summary와 여러 게임 전적 목록의 API 책임을 분리한다.

Backend:

- [x] endpoint를 확정한다.
  - `GET /api/v1/users/me/game-records`
- [x] query 정책을 확정한다.
  - `page`: 1-based, 기본값 1, 허용 범위 1~3
  - `size`: query로 받지 않고 서버 고정 10
  - 최신순 정렬: `GameRecord.createdAt desc`, `id desc`
  - 최근 30경기까지만 조회
- [x] response shape를 확정한다.
  - `page`
  - `size`
  - `totalPages`
  - `totalElements`
  - `hasNext`
  - `records[]`
  - `gameId`
  - `result`
  - `opponentUserId`
  - `opponentNickname`
  - `rankBefore`
  - `rankAfter`
  - `lpBefore`
  - `lpAfter`
  - `lpChange`
  - `playedAt`
- [x] `reason`은 현재 `GameRecord` source에 없으므로 이번 계약에서 제외한다.
- [x] 빈 목록, 인증 실패, pagination 오류 ErrorResponse를 정리한다.
- [x] RestDocs를 작성한다.

Policy:

- [x] Game Result Summary는 결과 직후 상세 확인용이다.
- [x] 전적 목록 API는 계정의 누적 기록 조회용이다.
- [x] Summary payload를 sessionStorage에서 복원해 전적 목록처럼 사용하지 않는다.
- [x] opponent nickname은 row별 단건 조회가 아니라 core `UserReadService.findAllByIdsOrThrow` batch 조회로 조립한다.
- [x] API 모듈은 core repository를 직접 참조하지 않는다.

Acceptance Criteria:

- [x] 프론트가 최근 경기 목록과 pagination UI를 구현할 수 있는 payload가 확정된다.
- [x] pagination 정책이 문서화된다.

### 3-2. [x] 전적 조회 UI 통합

담당: Frontend

우선순위: P2

목표:

- [x] 내 정보 화면에서 최근 전적 최대 30경기를 10개 단위 페이지로 확인할 수 있게 한다.

Frontend:

- [x] record service를 추가한다.
- [x] ProfilePage에서 최초 page 1을 조회한다.
- [x] ProfilePage에서 page 1/2/3 버튼으로 선택한 전적 page만 조회한다.
- [x] loading/error/empty 상태를 구현한다.
- [x] 한 경기당 가로 바 형태로 `나 VS 상대` 전적 row를 표시한다.
- [x] MatchPage 상단 내 정보 버튼을 `/profile`로 연결한다.
- [x] 별도 `/records` route와 `GameRecordsPage`를 제거한다.

Policy:

- [x] 전적 목록의 source of truth는 3-1 API다.
- [x] `GET /api/v1/users/me/game-records?page=1~3`만 전적 목록 source로 사용한다.
- [x] `size` query를 보내지 않고 서버 고정 10개 정책을 따른다.
- [x] Game Result Summary sessionStorage를 전적 목록 source로 사용하지 않는다.
- [x] Game Result WebSocket payload를 전적 목록 source로 사용하지 않는다.
- [x] 새 패키지는 추가하지 않는다.

Acceptance Criteria:

- [x] 내 정보 버튼 클릭 시 `/profile`로 이동한다.
- [x] 전적 API 성공 시 ProfilePage에 현재 선택한 최근 전적 page가 표시된다.
- [x] 비어 있는 경우 empty 상태가 표시된다.
- [x] 조회 실패는 ProfilePage 전적 섹션 내부 error로 처리된다.
- [x] 별도 `/records` route는 제공하지 않는다.

### 3-3. [x] 프로필 상세 API 계약

담당: Frontend

우선순위: P3

목표:

- [x] ProfilePage가 필요한 데이터는 신규 통합 API 없이 기존 3개 API 조합으로 충분하다고 확정한다.

Backend:

- [x] 2-1 profile API를 재사용한다.
- [x] 2-2 rank API를 재사용한다.
- [x] 3-1 game records API를 재사용한다.
- [x] 별도 profile detail endpoint를 만들지 않는다.
- [x] 최근 전적은 profile response에 포함하지 않고 3-1 record API page 1/2/3으로 조회한다.
- [x] 기존 RestDocs와 ErrorResponse 계약을 그대로 사용한다.

Policy:

- [x] Profile API는 계정 기본 정보 source of truth다.
- [x] Rank API는 현재 랭크/LP/승패 source of truth다.
- [x] Game Records API는 최근 전적 최대 30경기 source of truth다.
- [x] 프론트는 세 API 응답을 화면 local state에서만 조합한다.

Acceptance Criteria:

- [x] ProfilePage가 필요한 사용자 정보와 랭크 payload가 확정된다.
- [x] 최근 전적은 Game Records API page 1/2/3에서 가져온다고 문서화된다.

### 3-4. [x] 프로필 페이지 구현

담당: Frontend

우선순위: P3

목표:

- [x] 사용자 프로필과 랭크/승패/최근 전적 최대 30경기를 10개 단위 페이지로 확인할 수 있게 한다.

Frontend:

- [x] `/profile` route를 추가한다.
- [x] `ProfilePage`를 구현한다.
- [x] MatchPage 상단 내 정보 버튼과 프로필 영역에서 이동할 수 있게 한다.
- [x] 내 랭크, 승패, 최근 전적 page 1/2/3을 표시한다.
- [x] 기존 profile/rank/game records API를 계약에 맞게 조합한다.
- [x] 별도 `/records` route와 `GameRecordsPage`는 제거한다.

Policy:

- [x] MatchPage는 매칭 시작 화면이다.
- [x] ProfilePage는 계정 상태 상세 화면이다.
- [x] 두 화면이 같은 API를 쓰더라도 UI state는 각 page local state로 관리한다.

Acceptance Criteria:

- [x] 프로필 화면에서 사용자 정보와 랭크 요약을 확인할 수 있다.
- [x] 프로필 화면에서 최근 전적 최대 30경기를 10개 단위 페이지로 확인할 수 있다.
- [x] MatchPage와 동일한 source of truth 정책을 따른다.
- [x] 기록 조회 실패가 프로필 기본 정보 표시를 막지 않는다.

## Section 4. Optional Game Modes

### 4-1. [x] 연습 모드 API / Scenario 계약

담당: Backend

우선순위: P4

목표:

- [x] 실제 상대 없이 LIGHTNING/게임 플레이를 테스트할 수 있는 연습 game room 계약을 확정한다.

Backend:

- [x] 연습 게임 room 생성 API를 구현한다.
- [x] endpoint를 확정한다.
  - `POST /api/v1/games/practice`
- [x] request body 없음과 `@AuthUser Long userId` 인증 사용자 식별 정책을 확정한다.
- [x] response shape를 확정한다.
  - `gameRoomId`
  - `serverTime`
  - `startAt`
  - `webSocketUrl`
  - `scenario`
- [x] scenario 생성 정책을 확정한다.
  - 서버가 기존 `GameStartScenarioPayload`와 같은 shape의 `scenario`를 내려준다.
  - 연습 모드는 `opponent`, `matchId`를 내려주지 않는다.
- [x] 연습 room은 `gameMode=PRACTICE`로 저장한다.
- [x] 연습 room은 participant 1명으로 생성/시작한다.
- [x] WebSocket은 기존 `/ws/game/{gameRoomId}`를 재사용한다.
- [x] LIGHTNING kill 결과를 `GAME_RESULT`로 반환한다.
  - `reason=PRACTICE_LIGHTNING_KILL`
  - `practiceResult=SUCCESS`
- [x] 시나리오 종료까지 kill이 없으면 timeout 결과를 `GAME_RESULT`로 반환한다.
  - `reason=PRACTICE_TIMEOUT`
  - `practiceResult=FAILED`
- [x] 연습 결과를 랭크/LP/전적에 반영하지 않는 서버 정책을 구현/문서화한다.

Policy:

- [x] 연습 모드는 랭크/LP/전적 정산에 반영하지 않는다.
- [x] practice 결과는 Summary HTTP API가 아니라 `GAME_RESULT` WebSocket payload를 최종 source로 삼는다.
- [x] 일반 ranked match의 `GAME_RESULT -> Summary HTTP polling` 계약은 유지한다.
- [x] practice room은 settlement trigger, core settlement, recovery query, summary API에서 제외한다.
- [x] 기존 ranked match WebSocket 계약과 섞지 않는다.

Acceptance Criteria:

- [x] 프론트가 기존 handoff 구조로 연습 게임에 진입할 수 있는 payload가 확정된다.
- [x] 연습 결과 미정산 정책이 문서화된다.
- [x] RestDocs와 테스트가 작성된다.
- [x] 전체 백엔드 테스트가 통과한다.

### 4-2. [x] 연습 모드 프론트 진입 구현

담당: Frontend

우선순위: P4

목표:

- [x] MatchPage 연습 모드 버튼으로 실제 연습 게임 화면에 진입한다.

Frontend:

- [x] MatchPage 연습 모드 버튼을 4-1 API와 연결한다.
- [x] practice 생성 응답을 GamePlayPage handoff payload로 저장한다.
- [x] waiting page는 백엔드 payload 계약에 맞춰 생략한다.
- [x] 연습 모드 결과는 GamePlayPage 내부 오버레이로 랭크 매칭과 구분한다.

Policy:

- [x] 연습 모드 진입은 일반 match queue와 독립이다.
- [x] LIGHTNING 입력 테스트는 가능하지만 랭크/전적 결과와 섞지 않는다.

Acceptance Criteria:

- [x] 연습 모드 버튼으로 게임 화면에 진입할 수 있다.
- [x] LIGHTNING 입력을 테스트할 수 있다.
- [x] 연습 결과가 랭크/전적에 반영되지 않는다.

### 4-3. [x] Custom Room 생성 / 공개 목록 / 조회 API 계약

담당: Backend

우선순위: P4

목표:

- [x] 사용자가 사용자 지정 게임 방을 만들고, 공개 대기실 목록 또는 초대 코드로 방 상태를 조회할 수 있게 한다.
- [x] 초대 링크의 기반이 되는 `inviteCode`를 방 생성 시 발급한다.

Backend:

- [x] endpoint를 확정한다.
- [x] `POST /api/v1/custom-games/rooms`
- [x] `GET /api/v1/custom-games/rooms`
- [x] `GET /api/v1/custom-games/rooms/invites/{inviteCode}`
- [x] `CustomGameRoom`, `CustomGameParticipant` 기본 모델을 구현한다.
- [x] 방 생성자는 `OWNER` participant로 저장한다.
- [x] 최대 인원은 2명으로 고정한다.
- [x] room status는 `WAITING | STARTED | CLOSED`로 둔다.
- [x] `inviteCode`는 공유용 public key로 발급하고 unique 정책을 둔다.
- [x] 모든 `WAITING` custom room은 공개 대기실 목록에 노출한다.
- [x] 대기실 이름은 `{ownerNickname}'s room`으로 반환한다.
- [x] room state response를 확정한다.
  - `roomId`
  - `roomName`
  - `inviteCode`
  - `ownerUserId`
  - `status`
  - `maxParticipants`
  - `participants`
- [x] 참가자 nickname은 user id를 모아 batch 조회한다.
- [x] RestDocs와 ErrorResponse를 정리한다.

Policy:

- [x] core 모듈은 room/participant 영속성 계층과 도메인 규칙을 담당한다.
- [x] api 모듈은 repository에 직접 접근하지 않고 core service를 사용한다.
- [x] inviteCode는 roomId를 직접 공유하지 않기 위한 초대 식별자다.
- [x] roomName은 저장값이 아니라 owner nickname 기반 표시값이다.
- [x] inviteCode 공개 조회는 참가 처리가 아니라 방 미리보기 용도다.
- [x] 이번 이슈에서는 참가, 나가기, WebSocket, 게임 시작을 구현하지 않는다.

Acceptance Criteria:

- [x] 방 생성 시 owner participant와 inviteCode가 함께 생성된다.
- [x] 공개 대기실 목록 또는 초대 코드로 참가 가능한 방 상태를 확인할 수 있다.
- [x] custom room의 기본 모델과 API 문서가 정리된다.

### 4-4. [x] Custom Room 초대 참가 / 나가기 API 계약

담당: Backend

우선순위: P4

목표:

- [x] 초대 코드로 사용자 지정 방에 참가하고, 대기 중인 방에서 나갈 수 있게 한다.

Backend:

- [x] endpoint를 확정한다.
- [x] `POST /api/v1/custom-games/rooms/{inviteCode}/join`
- [x] `POST /api/v1/custom-games/rooms/{roomId}/leave`
- [x] room이 `WAITING`일 때만 참가를 허용한다.
- [x] 정원이 2명이면 참가를 거부한다.
- [x] 이미 참가한 사용자의 join은 idempotent하게 최신 room state를 반환한다.
- [x] 방장이 나가면 room을 `CLOSED`로 전환한다.
- [x] 일반 참가자가 나가면 participant row를 삭제한다.
- [x] RestDocs와 ErrorResponse를 정리한다.

Policy:

- [x] join/leave는 room lifecycle command다.
- [x] join/leave 이후 실시간 broadcast는 4-5 Room WebSocket 이슈에서 연결한다.
- [x] started/closed room에는 새 참가를 허용하지 않는다.
- [x] 대기실은 1명 또는 2명 상태를 허용하되, game start는 4-6의 2명 필수 정책을 따른다.

Acceptance Criteria:

- [x] inviteCode로 방에 참가할 수 있다.
- [x] 정원 초과, 시작된 방, 닫힌 방 참가가 거부된다.
- [x] 방장 퇴장 시 방이 닫힌다.

### 4-5. [x] Custom Room WebSocket 동기화 계약

담당: Backend

우선순위: P4

목표:

- [x] 사용자 지정 방 페이지에서 참가자 입장/퇴장과 방 상태 변경을 실시간으로 받을 수 있게 한다.

Backend:

- [x] WebSocket endpoint를 확정한다.
  - `/ws/custom-games/rooms/{roomId}?token={accessToken}`
- [x] token query parameter 인증 정책을 기존 Game WebSocket과 맞춘다.
- [x] room participant만 연결을 허용한다.
- [x] room별 session registry를 구현한다.
- [x] `ROOM_UPDATED` event payload를 확정한다.
- [x] `ROOM_CLOSED` event payload를 확정한다.
- [x] join/leave command 이후 room 상태 broadcast를 연결한다.

Policy:

- [x] Room WebSocket은 CustomRoomPage에서만 연결한다.
- [x] HTTP join/leave 응답은 command 결과이고, 다른 참가자 반영은 WebSocket event로 전달한다.
- [x] Room WebSocket은 게임 플레이 WebSocket과 분리한다.

Acceptance Criteria:

- [x] 참가자 입장/퇴장 시 room 참여자에게 최신 room state가 broadcast된다.
- [x] 닫힌 방은 `ROOM_CLOSED`로 전달된다.
- [x] 참가자가 아닌 사용자는 room socket에 연결할 수 없다.

### 4-6. [x] Custom Game Start API / ROOM_STARTED 계약

담당: Backend

우선순위: P4

목표:

- [x] 방장이 시작 버튼을 누르면 정확히 2명이 같은 custom game room과 scenario로 진입할 수 있게 한다.

Backend:

- [x] endpoint를 확정한다.
  - `POST /api/v1/custom-games/rooms/{roomId}/start`
- [x] 방장만 start를 호출할 수 있게 한다.
- [x] participant가 정확히 2명일 때만 시작 가능하게 한다.
- [x] room status가 `WAITING`일 때만 시작 가능하게 한다.
- [x] `gameMode=CUSTOM` GameRoom을 생성한다.
- [x] Scenario를 생성/저장한다.
- [x] room status를 `STARTED`로 전환한다.
- [x] HTTP start 응답은 이동 기준이 아니라 command ack로 둔다.
- [x] Room WebSocket `ROOM_STARTED` event payload를 확정한다.
  - `roomId`
  - `gameRoomId`
  - `gameMode=CUSTOM`
  - `startAt`
  - `webSocketUrl`
  - `scenario`

Policy:

- [x] Practice Mode는 1인 플레이를 담당하고, Custom Game은 2인 비랭크 대전을 담당한다.
- [x] 실제 GamePlayPage 이동 기준은 HTTP 응답이 아니라 `ROOM_STARTED` event다.
- [x] 방장과 참가자가 같은 `gameRoomId`, `scenario`, `startAt`을 받도록 WebSocket broadcast를 사용한다.
- [x] start 이후에는 custom room 참가/나가기를 허용하지 않는다.

Acceptance Criteria:

- [x] 방장만 사용자 지정 게임을 시작할 수 있다.
- [x] 1명 방은 시작할 수 없고 2명 방만 시작할 수 있다.
- [x] 모든 room socket 참가자에게 동일한 `ROOM_STARTED` payload가 전달된다.

### 4-7. [x] Custom Game 랭크 제외 / 전적 기록 / 결과 WebSocket 계약

담당: Backend

우선순위: P4

목표:

- [x] 2인 사용자 지정 게임 결과는 랭크/LP에는 반영하지 않고, 전적에는 남기도록 서버 결과 정책을 확정한다.

Backend:

- [x] 기존 Game WebSocket, LIGHTNING, Scenario, GameAction 로직을 재사용한다.
- [x] `GAME_RESULT.gameMode=CUSTOM` payload를 내려준다.
- [x] custom game result reason 정책을 확정한다.
- [x] custom game도 `game_records`에는 저장한다.
- [x] custom game record는 rank/LP 변화 없이 저장한다.
- [x] rank settlement trigger에서 `gameMode=CUSTOM`의 LP/rank 변경을 제외한다.
- [x] core rank settlement service에서 `gameMode=CUSTOM`의 rank 변경을 제외한다.
- [x] recovery scheduler/query에서 custom game record 누락은 복구하되 rank/LP 복구는 제외한다.
- [x] Summary API가 custom game의 결과/전적 저장 상태를 조회할 수 있는지 정책을 확정한다.

Policy:

- [x] `MATCH`는 랭크/LP/전적 반영 대상이다.
- [x] `CUSTOM`은 2인 전적 반영 대상이지만 랭크/LP 반영 대상이 아니다.
- [x] `PRACTICE`는 랭크/LP/전적 모두 미반영 대상이다.
- [x] custom 최종 결과는 WebSocket `GAME_RESULT`가 source of truth다.
- [x] custom 결과는 랭크 변화가 없는 전적 결과로 다룬다.

Acceptance Criteria:

- [x] custom game을 완료하면 전적이 생성된다.
- [x] custom game을 완료해도 rank/LP는 변경되지 않는다.
- [x] custom game result는 WebSocket payload로 확정된다.
- [x] settlement/recovery 테스트에서 custom record 저장과 rank/LP 제외가 검증된다.

### 4-8. [x] Custom Room 공개 대기실 / 초대 링크 프론트 구현

담당: Frontend

우선순위: P4

목표:

- [x] MatchPage의 사용자 지정 버튼으로 공개 대기실 목록을 보고, custom room을 만들거나 초대 코드로 방을 찾을 수 있게 한다.

Frontend:

- [x] custom room service를 추가한다.
- [x] MatchPage 사용자 지정 버튼을 공개 대기실 화면으로 연결한다.
- [x] 공개 대기실 화면에서 4-3 room list API를 호출한다.
- [x] 새 방 만들기 버튼을 4-3 create room API와 연결한다.
- [x] `/custom-games/rooms/:roomId` route를 추가한다.
- [x] CustomRoomPage에서 room 조회 API를 호출한다.
- [x] 대기실 목록에서 roomName, 현재 인원, 방장 정보를 표시한다.
- [x] 초대 링크를 표시한다.
- [x] 초대 코드 입력으로 4-3 invite preview API를 호출한다.
- [x] 초대 링크 복사 버튼을 구현한다.
- [x] 참가자 목록과 방장 표시를 구현한다.

Policy:

- [x] 사용자 지정 방 생성은 일반 match queue와 독립이다.
- [x] 모든 `WAITING` custom room은 공개 목록에 표시한다.
- [x] 초대 링크는 `inviteCode`를 사용하고 roomId를 공유하지 않는다.
- [x] Room WebSocket 연결은 4-9에서 구현한다.

Acceptance Criteria:

- [x] 사용자 지정 버튼으로 공개 대기실 목록을 볼 수 있다.
- [x] 새 방 만들기로 방을 만들 수 있다.
- [x] 방 페이지에서 초대 링크와 참가자 목록을 확인할 수 있다.
- [x] 방 생성 흐름이 매칭 queue 상태와 충돌하지 않는다.

### 4-9. [x] Custom Room 초대 참가 / WebSocket 프론트 구현

담당: Frontend

우선순위: P4

목표:

- [x] 초대 링크로 들어온 사용자가 사용자 지정 방에 참가하고, 참가자 목록 변경을 실시간으로 볼 수 있게 한다.

Frontend:

- [x] `/custom-games/join/:inviteCode` route를 추가한다.
- [x] 로그인하지 않은 사용자는 로그인 후 초대 링크로 복귀하게 한다.
- [x] join API를 호출하고 성공 시 `/custom-games/rooms/:roomId`로 이동한다.
- [x] CustomRoomPage에서 Room WebSocket을 연결한다.
- [x] `ROOM_UPDATED`로 참가자 목록을 갱신한다.
- [x] `ROOM_CLOSED`로 방 닫힘 상태를 표시하고 `/match` 복귀 액션을 제공한다.
- [x] 나가기 버튼을 4-4 leave API와 연결한다.

Policy:

- [x] Room WebSocket은 방 페이지에서만 연결한다.
- [x] HTTP join/leave는 command이고, 최종 room 상태 반영은 WebSocket event를 우선한다.
- [x] 친구 목록 기반 초대는 이번 범위에서 제외한다.

Acceptance Criteria:

- [x] 초대 링크로 방에 참가할 수 있다.
- [x] 참가자 입장/퇴장이 실시간으로 반영된다.
- [x] 닫힌 방/가득 찬 방/시작된 방 오류가 사용자에게 표시된다.

### 4-10. [ ] Custom Game 시작 / GamePlay 프론트 연결

담당: Frontend

우선순위: P4

목표:

- [ ] 방장이 시작하면 방장과 참가자가 같은 사용자 지정 게임 화면으로 이동한다.

Frontend:

- [ ] 방장에게만 시작 버튼을 표시한다.
- [ ] 시작 버튼은 4-6 start API를 호출한다.
- [ ] start HTTP 응답만으로 route 이동하지 않는다.
- [ ] `ROOM_STARTED` event 수신 시 custom game start payload를 저장한다.
- [ ] GamePlayPage가 `gameMode=CUSTOM` payload로 play state를 구성한다.
- [ ] custom game 시작 전 `3 / 2 / 1` countdown을 표시한다.
- [ ] custom 결과는 GamePlayPage 내부 오버레이로 표시한다.
- [ ] 결과 오버레이에 `다시 방으로`, `메인으로` 액션을 제공한다.

Policy:

- [ ] 사용자 지정 게임은 랭크/LP 변화 화면으로 보내지 않는다.
- [ ] custom 결과는 전적에 남지만 랭크 변화는 표시하지 않는다.
- [ ] custom 결과 Summary API 사용 여부는 4-7 백엔드 계약을 따른다.
- [ ] GamePlay WebSocket은 기존 `/ws/game/{gameRoomId}` 정책을 재사용한다.

Acceptance Criteria:

- [ ] 방장과 참가자가 `ROOM_STARTED` 기준으로 같은 게임에 진입한다.
- [ ] custom game 결과가 GamePlayPage 내부에 표시된다.
- [ ] custom game 결과가 후속 전적 조회에 남는 정책과 충돌하지 않는다.
- [ ] 일반 ranked match 결과 route/Summary API 흐름이 깨지지 않는다.

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
4. [x] Section 2-1. 내 프로필 조회 API 계약
5. [x] Section 2-2. 내 랭크 조회 API 계약
6. [x] Section 2-3. MatchPage 내 프로필 / 랭크 실데이터 구현
7. [x] Section 2-4. 랭킹 조회 API 계약
8. [x] Section 2-5. MatchPage 랭킹 실데이터 구현
9. [x] Section 3-1. 내 전적 목록 API 계약
10. [x] Section 3-2. 전적 조회 UI 통합
11. [x] Section 3-3. 프로필 상세 API 계약
12. [x] Section 3-4. 프로필 페이지 구현
13. [x] Section 4-1/4-2. 연습 모드
14. [x] Section 4-3. Custom Room 생성 / 공개 목록 / 조회 API 계약
15. [x] Section 4-4. Custom Room 초대 참가 / 나가기 API 계약
16. [x] Section 4-5. Custom Room WebSocket 동기화 계약
17. [x] Section 4-6. Custom Game Start API / ROOM_STARTED 계약
18. [x] Section 4-7. Custom Game 랭크 제외 / 전적 기록 / 결과 WebSocket 계약
19. [x] Section 4-8. Custom Room 공개 대기실 / 초대 링크 프론트 구현
20. [x] Section 4-9. Custom Room 초대 참가 / WebSocket 프론트 구현
21. [ ] Section 4-10. Custom Game 시작 / GamePlay 프론트 연결
22. [ ] Section 5-1/5-2. 비밀번호 찾기
23. [ ] Section 5-3/5-4. OAuth 로그인

## 판단 기준

- [x] “로그인부터 게임 결과까지”는 현재 MVP Core로 구현되어 있다.
- [x] “회원가입부터 매칭까지”라고 말하려면 Section 1-1이 필요하다.
- [x] “MatchPage가 실제 계정 상태를 보여준다”고 말하려면 Section 2-1부터 2-3까지 필요하다.
- [x] “MatchPage의 모든 주요 표시가 실데이터다”라고 말하려면 Section 2-1부터 2-5까지 필요하다.
- [x] “내 기록을 다시 볼 수 있다”고 말하려면 Section 3-1과 3-2가 필요하다.
- [x] “내 프로필 상세를 볼 수 있다”고 말하려면 Section 3-3과 3-4가 필요하다.
- [ ] “현재 화면의 모든 버튼이 기능한다”고 말하려면 Section 4까지 필요하다.
