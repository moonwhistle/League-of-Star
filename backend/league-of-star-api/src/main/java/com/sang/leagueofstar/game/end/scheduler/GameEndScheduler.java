package com.sang.leagueofstar.game.end.scheduler;

import com.sang.leagueofstar.game.end.common.constant.GameEndConstants;
import com.sang.leagueofstar.game.end.service.GameEndSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameEndScheduler {

    private final GameEndSettlementService gameEndSettlementService;

    @Scheduled(fixedDelayString = GameEndConstants.GAME_END_SCHEDULER_FIXED_DELAY_MS)
    public void processDueEndDeadlines() {
        try {
            gameEndSettlementService.processDueEndDeadlines();
        } catch (Exception e) {
            log.warn("Failed to process game end deadlines.", e);
        }
    }
}
