# League of Smite - Domain Status

이 문서는 League of Smite 프로젝트의 핵심 도메인별 상태 전환 흐름을 정의합니다.

## 1. User (유저 상태)
유저의 서비스 이용 상태 흐름입니다.

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: 회원가입 (Signup)
    ACTIVE --> WITHDRAWN: 회원 탈퇴 (Withdraw)
    WITHDRAWN --> ANONYMIZED: 데이터 파기 (개인정보 익명화)
    ANONYMIZED --> [*]
    
    note right of ACTIVE
        정상 서비스 이용 가능 상태
    end note
```

## 2. Match (매칭 상태)
매칭 큐에 진입한 순간부터 게임이 성사되기까지의 흐름입니다.

```mermaid
stateDiagram-v2
    [*] --> PENDING: 매칭 큐 진입 (Find Match)
    PENDING --> MATCHED: 상대 탐색 완료
    
    state MATCHED {
        [*] --> WAITING_ACCEPT: 수락 대기
        WAITING_ACCEPT --> ACCEPTED: 수락 (Accept)
        WAITING_ACCEPT --> REJECTED: 거절 (Decline)
        WAITING_ACCEPT --> TIMEOUT: 응답 시간 초과
    }
    
    ACCEPTED --> IN_GAME: 양측 모두 수락
    REJECTED --> PENDING: 한 명이라도 거절 시 (자동 재매칭)
    TIMEOUT --> [*]: 응답 시간 초과 시 (대기열 이탈)
    
    IN_GAME --> [*]: 게임 세션 생성 완료
```

## 3. Game (게임 세션 상태)
실제 게임이 진행되는 과정의 상태 흐름입니다.

```mermaid
stateDiagram-v2
    [*] --> READY: 게임 세션 생성 완료
    READY --> IN_PROGRESS: 유저 접속 및 시작 신호
    
    state IN_PROGRESS {
        [*] --> WAITING_ACTION: 강타 대기
        WAITING_ACTION --> SMITED: 강타 실행 (Smite Action)
    }
    
    SMITED --> FINISHED: 판정 완료 (Winner Decided)
    FINISHED --> RECORDED: 전적 기록 완료
    RECORDED --> [*]

    IN_PROGRESS --> ABORTED: 유저 탈주/연결 끊김 (DISCONNECTED)
    ABORTED --> [*]
```

## 4. Password Reset (비밀번호 재설정)
Redis에서 관리되는 비밀번호 재설정 토큰의 수명 주기입니다.

```mermaid
stateDiagram-v2
    [*] --> ISSUED: 재설정 요청 (Token 생성)
    ISSUED --> USED: 비밀번호 변경 성공
    ISSUED --> EXPIRED: 10분 경과 (TTL 만료)
    
    USED --> [*]: 토큰 즉시 삭제
    EXPIRED --> [*]: Redis 자동 삭제
```
