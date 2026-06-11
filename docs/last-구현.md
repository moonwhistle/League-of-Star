# Last 구현 정리

## 현재 판단

로그인 이후 매칭, 게임 대기, 게임 시작, 플레이, LIGHTNING, 결과 Summary까지 이어지는 프론트 코어 흐름은 구현되어 있다. 다만 “MVP 코어가 완전히 닫혔다”고 말하려면 아래 정합성 작업이 먼저 필요하다.

- `/match` route가 아직 임시 preview public 상태다.
- `front-plan.md` 하단 `Issue Split Recommendation` 체크 상태가 본문 완료 상태와 다르다.
- 회원가입은 현재 버튼만 있고 실제 기능은 후속 범위다.
- MatchPage의 랭크/기록/랭킹/연습 모드/사용자 지정은 MVP 코어가 아니라 부가 기능 또는 실데이터 연동 범위다.

따라서 남은 작업은 “MVP 코어 마감 → 랭크/프로필 조회 → 회원가입 → auth 고도화 → 부가 기능” 순서로 진행한다.

## Step 1. MVP Auth / Document Consistency 마감

우선순위: P0

목표:

- 로그인 후 매칭이라는 MVP 시작점을 인증 route 정책과 일치시킨다.
- 코드와 문서가 같은 완료 상태를 말하도록 정리한다.

Backend:

- 추가 구현 없음.
- 기존 Authorization 필수 API 정책을 유지한다.

Frontend:

- `/match` route에 `requiresAuth: true`를 복구한다.
- `router/index.test.ts`의 “match public preview” 테스트를 제거하고 protected route 테스트로 바꾼다.
- `authGuard` 테스트에서 `/match` 미인증 접근 시 `/login` 이동을 검증한다.
- `front-plan.md` 하단 `Issue Split Recommendation`의 매칭/게임 대기 완료 체크를 본문과 맞춘다.
- issue-74 문서의 `/match requiresAuth` 완료 상태와 실제 router meta를 일치시킨다.

검증:

- `npm run format`
- `npm run lint`
- `npm run typecheck`
- `npm run test`
- `npm run build`

## Step 2. 내 랭크 / 프로필 조회 API 계약 확정

우선순위: P1

목표:

- MatchPage에 표시할 “현재 내 계정 상태”의 백엔드 source of truth를 확정한다.
- 게임 결과 Summary의 `me/opponent`와 MatchPage의 현재 랭크 정보를 섞지 않는다.

Backend:

- 내 프로필/랭크 조회 API를 확인하거나 구현한다.
- 후보 endpoint:
  - `GET /api/v1/me/profile`
  - `GET /api/v1/users/me/rank`
- 최소 응답 후보:
  - `userId`
  - `nickname`
  - `rank`
  - `lp`
  - `tierScore`
  - `wins`
  - `losses`
  - `draws`
  - `seriesType`
  - `rankSeriesId`
- RestDocs와 ErrorResponse를 정리한다.

Frontend:

- 아직 구현하지 않고 계약만 확정한다.
- 어떤 UI 영역이 profile/rank API를 source로 삼는지 문서화한다.

정책:

- Result Summary API는 “방금 끝난 게임의 정산 결과”다.
- MatchPage profile/rank API는 “현재 내 계정 상태”다.

## Step 3. MatchPage 랭크 / 전적 UI 실데이터 전환

우선순위: P1

목표:

- MatchPage의 정적 랭크/전적 표시를 실제 API 데이터로 교체한다.

Backend:

- Step 2에서 확정한 API를 제공한다.
- 랭킹 목록까지 같이 할지, 내 정보만 먼저 할지 분리한다.

Frontend:

- profile/rank service를 추가한다.
- MatchPage mount 시 내 랭크 정보를 조회한다.
- loading/error/empty 상태를 표시한다.
- 기존 매칭 시작/취소/SSE 상태와 독립적인 page local state로 관리한다.
- 새 패키지는 추가하지 않는다.

테스트:

- API 성공 시 nickname/rank/lp/전적 표시.
- API 실패 시 매칭 버튼 흐름이 깨지지 않는지 검증.
- token은 기존 `apiClient` Authorization header 정책을 사용함을 검증.

## Step 4. 랭킹 / 기록 조회 분리 구현

우선순위: P2

목표:

- MatchPage의 랭킹/기록성 UI를 실제 데이터로 전환한다.
- 매칭 코어 플로우와 부가 조회 상태를 분리한다.

Backend:

- 랭킹 API 후보:
  - `GET /api/v1/rankings?limit=...`
- 내 최근 기록 API 후보:
  - `GET /api/v1/me/game-records`
  - `GET /api/v1/games/me/summaries`
- pagination/limit 정책을 정한다.

Frontend:

- ranking service와 record service를 분리한다.
- MatchPage의 랭킹/기록 UI를 API 데이터로 교체한다.
- 조회 실패가 매칭 시작/취소 기능을 막지 않게 한다.

정책:

- 랭킹/기록 조회는 MVP 코어 매칭 상태와 독립이다.
- 조회 실패는 부가 UI error로만 표시한다.

## Step 5. 회원가입 화면 구현

우선순위: P2

목표:

- 사용자가 실제로 회원가입 후 로그인까지 갈 수 있게 한다.

Backend:

- 회원가입 API 계약을 확인하거나 구현한다.
- 후보 endpoint:
  - `POST /api/v1/auth/signup`
- request 후보:
  - `{ email, password, nickname }`
- 성공 정책을 정한다.
  - 추천: 가입 성공 후 `/login` 이동.
  - 대안: 가입 성공 시 token 발급 후 `/match` 이동.
- 이메일/닉네임 중복 ErrorResponse를 문서화한다.

Frontend:

- `/signup` route를 추가한다.
- LoginPage의 `회원가입` 버튼을 `/signup`으로 연결한다.
- SignupPage를 구현한다.
- 성공 시 `/login` 이동을 기본 정책으로 삼는다.
- 실패 시 백엔드 ErrorResponse.message를 표시한다.

테스트:

- 회원가입 성공.
- 중복 이메일/닉네임 실패.
- 필수 입력 validation.
- 로그인 페이지 이동.

## Step 6. Auth refresh / logout 고도화

우선순위: P3

목표:

- token 만료와 로그아웃 UX를 정리한다.

Backend:

- refresh API 확인 또는 구현.
  - `POST /api/v1/auth/refresh`
- logout API가 필요한지 결정한다.
- 401/403 ErrorResponse 정책을 정리한다.

Frontend:

- 401 발생 시 refresh/retry 정책을 구현한다.
- refresh 실패 시 token clear 후 `/login`으로 이동한다.
- MatchPage logout 버튼을 실제 `clearAuthTokens()`와 route 이동에 연결한다.
- `userId`, `nickname` 저장 위치를 결정한다.

정책:

- access token 존재 여부만 보는 현재 guard는 MVP 1차로 충분하지만, 만료 검증은 후속 고도화 범위다.
- 전역 auth store는 필요성이 생길 때 도입한다.

## Step 7. 부가 모드 / 기타 기능

우선순위: P4

목표:

- MVP 코어 이후 화면에 있는 부가 버튼을 실제 기능으로 연결한다.

Backend:

- 연습 모드 game 생성 API가 필요하다.
- 사용자 지정 게임은 room 생성/초대/입장 API가 필요하다.
- OAuth는 redirect/callback/session 정책이 필요하다.
- 비밀번호 찾기는 이메일 발송/토큰/재설정 API가 필요하다.

Frontend:

- 연습 모드 버튼 연결.
- 사용자 지정 게임 화면/모달 구현.
- Google OAuth 버튼 연결.
- 비밀번호 찾기 route/page 구현.
- About page 또는 modal 구현.

정책:

- 이 단계는 MVP 코어가 아니다.
- 매칭/게임 결과 흐름과 독립 이슈로 분리한다.

## 추천 이슈 순서

1. Issue 98. MVP Auth / Document Consistency 마감
2. Issue 100. 내 랭크 / 프로필 조회 API 계약 확정
3. Issue 102. MatchPage 랭크 / 전적 UI 실데이터 전환
4. Issue 104. 랭킹 / 기록 조회 분리 구현
5. Issue 106. 회원가입 화면 구현
6. Issue 108. Auth refresh / logout 고도화
7. Issue 110. 연습 모드 / 사용자 지정 / OAuth / 비밀번호 찾기

## 마지막 기준

- “로그인부터 게임 결과까지”는 Step 1만 마감하면 MVP 코어 완료라고 볼 수 있다.
- “회원가입부터 매칭까지”라고 말하려면 Step 5까지 필요하다.
- “랭크 정보가 화면에 제대로 나온다”고 말하려면 Step 2와 Step 3이 필요하다.
- “현재 화면의 모든 버튼이 기능한다”고 말하려면 Step 7까지 필요하다.
