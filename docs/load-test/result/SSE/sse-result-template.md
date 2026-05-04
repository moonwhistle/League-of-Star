# SSE 매칭 알림 부하 테스트 결과

## 테스트 정보

| 항목 | 값 |
| --- | --- |
| SSE 구현 | MVC SseEmitter / WebFlux Netty |
| 테스트 모드 | connection / match |
| 실행 시각 |  |
| API 인스턴스 | 2대 |
| CONNECTIONS |  |
| HOLD_DURATION |  |
| RAMP_UP |  |
| JOIN_TPS |  |

## 결과 요약

| 지표 | 결과 | 판단 |
| --- | ---: | --- |
| 활성 SSE 연결 최대치 |  |  |
| SSE 연결 성공률 |  |  |
| SSE 연결 유지 시간 p95 |  |  |
| heartbeat 전송 실패율 |  |  |
| match_found 전송 실패율 |  |  |
| SSE 이벤트 전송 p95 |  |  |
| SSE 이벤트 전송 p99 |  |  |
| JVM Thread 최대치 |  |  |
| JVM Heap 최대치 |  |  |
| Process CPU 최대치 |  |  |

## Grafana 이미지

### 활성 연결 수 / 이벤트 전송

<!-- 이미지 첨부 -->

### JVM Thread / Heap / CPU

<!-- 이미지 첨부 -->

### 연결 종료 사유 / 전송 실패율

<!-- 이미지 첨부 -->

## 해석

### 1. 연결 수용량

- 목표 연결 수:
- 실제 활성 연결 최대치:
- 판단:

### 2. 이벤트 전송 안정성

- heartbeat:
- match_found:
- 전송 실패율:
- 판단:

### 3. 리소스 사용량

- Thread:
- Heap:
- CPU:
- 판단:

### 4. MVC vs Netty 전환 판단

```text
MVC SseEmitter가 목표 연결 수에서 thread, heap, CPU, 이벤트 전송 p95/p99를 안정적으로 유지했는가?
```

- 결론:
- 다음 개선:
