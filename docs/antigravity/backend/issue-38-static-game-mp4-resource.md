# Issue 38. Static Game MP4 Resource

## 📌 Feature Description

클라이언트 게임 화면에서 공통 MP4를 사용할 수 있도록 Spring Boot static resource 경로를 준비한다.

MVP에서는 gameRoom마다 MP4를 생성하거나 복제하지 않는다.
서버는 모든 gameRoom에 같은 `videoUrl`을 내려주고, 실제 MP4 파일은 로컬/배포 환경에서 정해진 static 경로에 배치한다.

```text
videoUrl = /assets/game/dragon-view.mp4
resource path = backend/smite-api/src/main/resources/static/assets/game/dragon-view.mp4
```

실제 MP4 파일은 바이너리이므로 Git에 커밋하지 않는다.
로컬 화면 테스트나 배포 환경에서만 `dragon-view.mp4`를 위 경로에 배치한다.

## 📚 Tasks

### 1. static resource 디렉토리 준비

- [x] `backend/smite-api/src/main/resources/static/assets/game` 디렉토리 추가
- [x] 빈 디렉토리 유지를 위한 `.gitkeep` 추가
- [x] 실제 `dragon-view.mp4` 파일은 Git 커밋 대상에서 제외

### 2. MP4 URL 정책 확인

- [x] `GameRoomSetupService`의 `videoUrl`이 `/assets/game/dragon-view.mp4`인지 확인
- [x] gameRoom별 MP4 생성/복제 로직이 없는지 확인
- [x] `match_response_result.game.videoUrl`이 고정 URL로 내려가는지 확인

### 3. 테스트 유지/보강

- [x] `GameRoomSetupServiceTest`에서 `videoUrl` 반환값 검증 유지
- [x] `GameSetupPortAdapterTest`에서 `videoUrl` 매핑 검증 유지
- [x] notification factory/dto 테스트에서 `game.videoUrl` 직렬화 검증 유지
- [x] 필요 시 static resource 경로 존재 여부를 가벼운 테스트로 검증

### 4. 문서 정합성 반영

- [x] `plan-checkpoint` Step 2 체크 상태 갱신
- [x] 실제 MP4 배치 경로 명시
- [x] MP4 파일은 repo에 포함하지 않는 정책 명시
- [x] CDN/S3 분리는 MVP 이후로 유지

## ✅ 완료 기준

- static resource 디렉토리가 repo에 존재한다.
- `videoUrl`은 `/assets/game/dragon-view.mp4`로 유지된다.
- 실제 MP4 파일 없이도 테스트가 통과한다.
- 로컬에서 `dragon-view.mp4`를 배치하면 Spring Boot static resource로 제공 가능한 구조다.

## 📝 Note

- 실제 파일 배치 위치:

```text
backend/smite-api/src/main/resources/static/assets/game/dragon-view.mp4
```

- Git에는 `.gitkeep`만 포함하고 MP4 파일은 포함하지 않는다.
- 운영 단계에서는 CDN/S3 분리를 후속 이슈로 검토한다.

## 📌 Related Issue

- Closes #38

----

## PR

## 📌 Summary

클라이언트 게임 화면에서 사용할 공통 MP4의 static resource 경로를 준비했습니다.
서버는 모든 gameRoom에 동일한 `videoUrl=/assets/game/dragon-view.mp4`을 내려주고, 실제 MP4 파일은 로컬/배포 환경에서 정해진 경로에 배치합니다.

## 📚 Changes

- static resource 경로 준비
  - `backend/smite-api/src/main/resources/static/assets/game` 디렉토리 추가
  - 빈 디렉토리 유지를 위해 `.gitkeep` 추가
  - 실제 MP4 파일은 Git에 포함하지 않도록 `.gitignore`에 `*.mp4` 제외 규칙 추가

- MP4 제공 정책 정리
  - MVP에서는 gameRoom마다 MP4를 생성하거나 복제하지 않음
  - `GameRoomSetupService`는 고정 `videoUrl=/assets/game/dragon-view.mp4`만 반환
  - 영상 파일 자체는 애플리케이션 코드 변경 없이 로컬/배포 환경에 배치 가능

- 설계 선택 이유
  - MP4는 바이너리 파일이라 Git diff/review가 어렵고 repo 크기를 불필요하게 키울 수 있음
  - 이번 이슈의 검증 대상은 영상 내용이 아니라 static resource 경로와 URL 계약임
  - `.gitkeep`만 커밋해 경로 계약은 보존하고, 실제 파일 배치는 환경별 책임으로 분리함
  - CDN/S3 분리는 MVP 이후로 미루고, 현재는 Spring Boot static resource 경로만 확정함

- 테스트/문서
  - 기존 `videoUrl` 반환/매핑/직렬화 테스트 유지 확인
  - static game asset 경로가 classpath resource에 포함되는지 가벼운 테스트 추가
  - `plan-checkpoint`와 issue 문서에 실제 MP4 배치 경로 및 repo 미포함 정책 반영

## 📝 Note

- 실제 MP4 파일 배치 위치:

```text
backend/smite-api/src/main/resources/static/assets/game/dragon-view.mp4
```

- 로컬 테스트 시 위 경로에 파일을 두면 `/assets/game/dragon-view.mp4`로 접근할 수 있습니다.
- `dragon-view.mp4`는 `.gitignore` 대상이므로 커밋하지 않습니다.

## 📌 Related Issue

- Closes #38




----

## 변경 이력

| 날짜 | 변경 내용 |
| :--- | :--- |
| 2026-05-14 | Issue 38 작업 문서 생성. 공통 MP4 static resource 디렉토리, URL 정책, 테스트/문서 task 정리 |
| 2026-05-14 | static resource 디렉토리와 `.gitkeep` 추가, MP4 파일 Git 제외 정책 반영 |
| 2026-05-14 | MP4 URL 정책 확인. `videoUrl=/assets/game/dragon-view.mp4`, gameRoom별 생성/복제 없음, notification payload 고정 URL 매핑 확인 |
| 2026-05-14 | 기존 `videoUrl` 테스트 유지 확인 및 static game asset classpath 경로 테스트 추가 |
| 2026-05-14 | 문서 정합성 반영. plan-checkpoint Step 2, 실제 MP4 배치 경로, repo 미포함 정책, CDN/S3 후속 정책 갱신 |
