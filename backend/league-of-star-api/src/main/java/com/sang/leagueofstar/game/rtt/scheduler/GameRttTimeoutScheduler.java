package com.sang.leagueofstar.game.rtt.scheduler;

import com.sang.leagueofstar.game.rtt.common.constant.GameRttConstants;
import com.sang.leagueofstar.game.rtt.service.GameRttMeasurementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameRttTimeoutScheduler {

    private final GameRttMeasurementService gameRttMeasurementService;

    @Scheduled(fixedDelayString = GameRttConstants.RTT_TIMEOUT_SCHEDULER_FIXED_DELAY_MS)
    public void processTimeouts() {
        try {
            gameRttMeasurementService.failTimedOutPings();
        } catch (Exception e) {
            log.warn("Failed to process RTT timeout.", e);
        }
    }
}
