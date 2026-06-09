package com.sang.leagueofstar.game.waiting.scheduler;

import com.sang.leagueofstar.game.waiting.common.constant.GameWaitingConstants;
import com.sang.leagueofstar.game.waiting.service.GameWaitingTimeoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 게임 대기 WebSocket timeout 처리를 주기적으로 트리거합니다.
 */
@Component
@RequiredArgsConstructor
public class GameWaitingTimeoutScheduler {

    private final GameWaitingTimeoutService gameWaitingTimeoutService;

    @Scheduled(fixedDelayString = GameWaitingConstants.WAITING_TIMEOUT_SCHEDULER_FIXED_DELAY_MS)
    public void processTimeouts() {
        gameWaitingTimeoutService.processTimeouts();
    }
}
