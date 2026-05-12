package com.sang.smite.matching.scheduler;

import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.service.MatchResponseTimeoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매칭 응답 timeout 처리를 주기적으로 트리거합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchResponseTimeoutScheduler {

    private final MatchResponseTimeoutService matchResponseTimeoutService;

    /**
     * 스케줄 주기에 맞춰 timeout 처리 서비스를 실행합니다.
     */
    @Scheduled(fixedDelayString = MatchingConstants.TIMEOUT_SCHEDULER_FIXED_DELAY_MS)
    public void processTimeouts() {
        matchResponseTimeoutService.processTimeouts();
    }
}
