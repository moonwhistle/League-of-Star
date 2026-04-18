---
trigger: glob
globs: ["*.tsx", "*.ts", "*.jsx", "*.js"]
---

# Frontend Convention (React 19 / TypeScript)

> **활성화**: Glob — `*.tsx, *.ts, *.jsx, *.js`

---

## 1. Naming Conventions

### 1.1 기본 원칙

- 축약어 지양, 역할과 의미가 드러나는 이름 사용.
- 일관성 유지: 컴포넌트, 훅, 상수의 네이밍 규칙을 명확히 구분.

### 1.2 컴포넌트

- **PascalCase** 사용.
- 파일명 = 컴포넌트명 일치 필수.
- 1 파일 = 1 컴포넌트.
- 예시: `GameHpBar.tsx`, `MatchAcceptModal.tsx`

### 1.3 Hooks

- 반드시 **`use` 접두사** 사용.
- 관리 대상이 명확히 드러나는 이름.
- ✅ `useMatchQueue()`, `useGameWebSocket()`, `useRankInfo()`
- ❌ `useData()`, `useLogic()`

### 1.4 변수 & 함수

- **lowerCamelCase** 사용.
- boolean은 의미 명확하게: `isLoggedIn`, `hasNextPage`, `shouldShowResult`

### 1.5 상수

- **UPPER_SNAKE_CASE** 사용.
- 의미 단위로 묶어 정의.
- 예시: `MAX_RETRY_COUNT`, `SMITE_DAMAGE`, `DRAGON_MAX_HP`

---

## 2. 디렉토리 구조

```
frontend/
└── src/
    ├── components/     # 재사용 가능한 UI 컴포넌트
    ├── pages/          # 라우트 단위 컴포넌트
    ├── hooks/          # Custom Hooks
    ├── services/       # API 호출 로직
    ├── stores/         # 상태 관리
    ├── utils/          # 순수 유틸 함수
    ├── types/          # 전역 타입 정의
    └── constants/      # 전역 상수
```

### 의존성 방향 (핵심)

```
pages → components, hooks, services, stores
         ↓
components → hooks, utils, types, constants
         ↓
hooks → services, utils, types
         ↓
services → types, constants
         ↓
utils, types, constants → (독립, 의존 없음)
```

**규칙**:
- 의존성은 **위에서 아래로**만 흐른다. 역방향 의존 절대 금지.
- **components**는 services를 직접 호출하지 않는다. hooks를 통해 접근한다.
- **hooks**는 components를 import하지 않는다.
- **services**는 순수 API 호출 로직만. UI 관련 코드 금지.
- **utils, types, constants**는 리프 노드(leaf node). 어디에도 의존하지 않는다.
- 같은 계층 내 import는 허용하되, **순환 의존은 금지**.

---

## 3. 컴포넌트 책임 분리

### Presentational Component

- UI 표현만 담당.
- props 기반으로 동작.
- API 호출, 전역 상태 접근 금지.

### Container / Page

- 데이터 조회 (hooks 사용).
- 상태 관리.
- 이벤트 핸들링.

하나의 컴포넌트에서 API 호출 + 상태 관리 + UI 렌더링을 **모두 처리하지 않는다.**

---

## 4. State & Side Effects

### 4.1 상태 관리 기준

| 종류 | 도구 |
|------|------|
| 로컬 상태 | `useState` |
| 서버 상태 | React Query |
| 전역 상태 | 정말 필요한 경우에만 (Zustand 등) |

### 4.2 Side Effect 관리

- `useEffect` 사용 **최소화.**
- 의존성 배열 **정확히** 명시.
- `eslint-disable` 남발 금지.

---

## 5. TypeScript 규칙

### 5.1 타입 안정성

- **`any` 사용 금지.**
- 공용 인터페이스 및 도메인 타입은 명시적으로 선언.

### 5.2 Props 타입

- Props 타입은 **반드시 분리** 정의.
- 재사용 가능하면 `types/` 디렉토리로 이동.

```typescript
interface GameHpBarProps {
  currentHp: number;
  maxHp: number;
  onSmite: () => void;
}
```

---

## 6. Coding Style

### 6.1 함수 설계

- 1 함수 = 1 역할.
- 조건 분기 깊이 최소화.
- **Early Return** 패턴 적극 활용.

### 6.2 렌더링 로직

- JSX 내부에 복잡한 조건문 금지.
- 렌더링에 필요한 값은 JSX 밖에서 사전 계산.

---

## 7. Error Handling

- API 에러는 **공통 레이어**(서비스/인터셉터)에서 처리.
- 컴포넌트 내부에서 `try-catch` 남발 금지.
- 사용자 메시지와 개발 로그를 **분리**.

---

## 8. WebSocket & Canvas (게임 특화)

- WebSocket 연결/해제는 **Custom Hook**으로 캡슐화. (`useGameWebSocket`)
- STOMP 메시지 핸들러는 Hook 내부에서 관리, 컴포넌트는 상태만 구독.
- Canvas 렌더링 로직은 `utils/` 또는 별도 `game/` 디렉토리에 분리.
- Canvas와 React 상태 동기화 시 `requestAnimationFrame` 기반으로 처리.

---

## 9. Testing

- **React Testing Library** 사용 권장.
- 테스트는 **구현이 아닌 행동**을 검증.
- 예시: "강타 버튼 클릭 시 서버에 SMITE 액션이 전송된다"
