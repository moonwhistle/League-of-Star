---
trigger: glob
globs: ["*.vue", "*.ts", "*.js"]
---

# Frontend Convention (Vue 3 / TypeScript)

> **활성화**: Glob — `*.vue, *.ts, *.js`

---

## 1. Naming Conventions

### 1.1 기본 원칙

- 축약어 지양, 역할과 의미가 드러나는 이름 사용.
- 일관성 유지: 페이지, 컴포넌트, composable, 상수의 네이밍 규칙을 명확히 구분.

### 1.2 Vue 컴포넌트

- **PascalCase** 사용.
- 파일명 = 컴포넌트명 일치 필수.
- 1 파일 = 1 컴포넌트.
- 예시: `GameHpBar.vue`, `MatchAcceptModal.vue`

### 1.3 Composables

- 반드시 **`use` 접두사** 사용.
- 관리 대상이 명확히 드러나는 이름.
- 예시: `useMatchQueue()`, `useGameWebSocket()`, `useRankInfo()`
- 단순 유틸 함수는 composable로 만들지 않고 `utils/` 또는 도메인 모듈에 둔다.

### 1.4 변수 & 함수

- **lowerCamelCase** 사용.
- boolean은 의미 명확하게: `isLoggedIn`, `hasNextPage`, `shouldShowResult`

### 1.5 상수

- **UPPER_SNAKE_CASE** 사용.
- 의미 단위로 묶어 정의.
- 예시: `MAX_RETRY_COUNT`, `LIGHTNING_DAMAGE`, `STAR_CORE_MAX_HP`

---

## 2. 디렉토리 구조

```text
frontend/
└── src/
    ├── components/     # 재사용 가능한 UI 컴포넌트
    ├── pages/          # 라우트 단위 Vue 컴포넌트
    ├── composables/    # 화면/브라우저 side effect 캡슐화가 필요할 때만 사용
    ├── services/       # API 호출 로직
    ├── stores/         # Pinia는 필요해지는 이슈에서만 도입
    ├── game/           # 게임 런타임 계산, WebSocket 메시지, HP scenario 유틸
    ├── utils/          # 순수 유틸 함수
    ├── types/          # 전역 타입 정의
    └── constants/      # 전역 상수
```

### 의존성 방향

```text
pages -> components, composables, services, game, types, constants
components -> composables, game, utils, types, constants
composables -> services, game, utils, types, constants
services -> types, constants
game -> types, constants
utils, types, constants -> no app dependency
```

**규칙**:

- 의존성은 위에서 아래로만 흐른다. 역방향 의존 금지.
- `components`는 API를 직접 호출하지 않는다. API 연결은 `pages` 또는 `composables`에서 조립한다.
- `composables`는 Vue 컴포넌트를 import하지 않는다.
- `services`는 순수 API 호출 로직만 담당하고 UI 상태를 알지 않는다.
- `game`은 HP scenario, message parsing, countdown 계산 같은 순수 런타임 로직 중심으로 둔다.
- `utils`, `types`, `constants`는 리프 노드로 유지한다.
- 같은 계층 내 import는 허용하되 순환 의존은 금지한다.

---

## 3. 컴포넌트 책임 분리

### Presentational Component

- UI 표현만 담당.
- props와 emits 기반으로 동작.
- API 호출, 라우터 이동, 전역 상태 접근 금지.

### Page Component

- 라우트 단위 조립을 담당.
- 데이터 조회, 이벤트 연결, 라우터 이동을 담당.
- 복잡한 side effect는 composable 또는 service로 분리한다.

하나의 컴포넌트에서 API 호출, 상태 관리, UI 렌더링을 모두 처리하지 않는다.

---

## 4. State & Side Effects

### 4.1 상태 관리 기준

| 종류 | 도구 |
|------|------|
| 로컬 상태 | Vue `ref`, `reactive`, `computed` |
| 화면 조립 상태 | Page component 또는 composable |
| 서버 상태 | 이번 이슈에서는 typed service 함수만 사용 |
| 전역 상태 | Pinia는 필요해지는 후속 이슈에서 작게 도입 |

- TanStack Query Vue는 초기 골격 작업에서 도입하지 않는다.
- API 호출 패턴이 반복되고 캐싱/무효화 요구가 명확해지면 별도 이슈로 도입을 검토한다.

### 4.2 Side Effect 관리

- `watch`, `watchEffect` 사용을 최소화하고 의존성을 명확히 둔다.
- WebSocket/EventSource 연결과 해제는 생명주기 훅에서 반드시 정리한다.
- `eslint-disable` 남발 금지.

---

## 5. TypeScript 규칙

### 5.1 타입 안정성

- `any` 사용 금지.
- 공용 인터페이스 및 도메인 타입은 명시적으로 선언.
- 백엔드 DTO, SSE event, WebSocket message는 `types/` 또는 `game/` 하위에 명시한다.

### 5.2 Props / Emits 타입

- Props와 emits 타입은 명시적으로 선언한다.
- 재사용 가능하면 `types/` 디렉토리로 이동한다.

```vue
<script setup lang="ts">
interface GameHpBarProps {
  currentHp: number;
  maxHp: number;
}

defineProps<GameHpBarProps>();

const emit = defineEmits<{
  lightning: [];
}>();
</script>
```

---

## 6. Coding Style

### 6.1 함수 설계

- 1 함수 = 1 역할.
- 조건 분기 깊이 최소화.
- Early return 패턴 적극 활용.

### 6.2 Template 로직

- Vue template 내부에 복잡한 조건식과 계산식을 두지 않는다.
- 렌더링에 필요한 값은 `computed` 또는 script 영역에서 사전 계산한다.
- `v-if`와 `v-for`를 같은 엘리먼트에 함께 사용하지 않는다.

---

## 7. Error Handling

- API 에러는 공통 서비스 레이어에서 기본 형태를 정리한다.
- 컴포넌트 내부에서 `try-catch` 남발 금지.
- 사용자 메시지와 개발 로그를 분리한다.

---

## 8. SSE, WebSocket & Game Rendering

- 매칭 알림 SSE는 브라우저 기본 `EventSource`를 사용한다.
- 게임 통신은 native `WebSocket`과 JSON message를 사용한다. STOMP.js, SockJS fallback은 MVP에서 사용하지 않는다.
- 연결/해제는 `services/realtime/`의 wrapper와 필요 시 composable로 캡슐화한다.
- WebSocket message handler는 composable 또는 `game/` 모듈 내부에서 관리하고, 컴포넌트는 상태와 command 함수만 사용한다.
- MP4 배경은 HTML `<video>`로 렌더링한다.
- HP bar, countdown, result HUD는 Vue 컴포넌트와 CSS overlay로 렌더링한다.
- HP scenario 계산은 `game/` 또는 `utils/`의 순수 함수로 분리한다.
- `startAt` 기준 HP overlay 갱신은 `requestAnimationFrame` 기반으로 처리한다.
- PixiJS, Web Worker, OffscreenCanvas는 MVP에서 사용하지 않는다. 실제 성능 문제가 확인되면 별도 이슈로 검토한다.

---

## 9. Testing

- Vitest + Vue Test Utils 사용 권장.
- 테스트는 구현이 아닌 행동을 검증.
- 예시: "강타 버튼 클릭 시 서버에 LIGHTNING 액션이 전송된다"

---

## 10. 변경 이력

| 날짜 | 변경 내용 |
| :--- | :--- |
| 2026-06-01 | 프론트엔드 기준을 Vue 3 + Vite + TypeScript로 전환. Pinia/TanStack Query Vue는 후속 이슈에서 필요 시 도입하기로 보류 |
| 2026-05-13 | MVP 프론트엔드 스택을 native EventSource/WebSocket, HTML video, React/CSS overlay 중심으로 단순화. STOMP.js/SockJS/PixiJS/Web Worker/OffscreenCanvas는 MVP 이후 검토로 정리 |
