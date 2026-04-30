package com.sang.smite.matching.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매칭 엔진 워커(Worker)
 *
 * <p>주기적으로 전체 Redis 대기열 스냅샷을 인메모리로 로드한 뒤,
 * 가장 오래 대기한 유저(FIFO)부터 슬라이딩 윈도우 방식으로 상대를 찾아 페어링합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEngine {

    private static final String MATCH_ENGINE_FIXED_DELAY_MS = "1000";

    /**
     * 매칭 엔진 스캔 루프 (1초마다 반복)
     */
    @Scheduled(fixedDelayString = MATCH_ENGINE_FIXED_DELAY_MS)
    public void processMatching() {
        log.debug("[MatchEngine] 스캔 루프 시작");

        // TODO 1: 전역 분산 락(lock:match:engine) 획득
        
        // TODO 2: 전체 대기열 조회 및 FIFO(entryTime 오름차순) 정렬
        
        // TODO 3: 슬라이딩 윈도우 페어링 및 atomicPairRemove 연동
        
        // TODO 4: 매칭 성사 시 상태 변경 (FOUND) 및 수락 세션 생성
        
        log.debug("[MatchEngine] 스캔 루프 완료");
    }
}
