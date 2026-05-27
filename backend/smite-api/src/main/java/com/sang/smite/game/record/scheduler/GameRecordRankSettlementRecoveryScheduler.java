package com.sang.smite.game.record.scheduler;

import com.sang.smite.game.record.common.constant.GameRecordConstants;
import com.sang.smite.game.record.service.GameRecordRankSettlementRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameRecordRankSettlementRecoveryScheduler {

    private final GameRecordRankSettlementRecoveryService gameRecordRankSettlementRecoveryService;

    @Scheduled(fixedDelayString = GameRecordConstants.RECORD_RECOVERY_SCHEDULER_FIXED_DELAY_MS)
    public void recoverUnsettledFinishedGameRooms() {
        try {
            gameRecordRankSettlementRecoveryService.recoverUnsettledFinishedGameRooms();
        } catch (Exception e) {
            log.warn("Failed to recover unsettled finished game rooms.", e);
        }
    }
}
