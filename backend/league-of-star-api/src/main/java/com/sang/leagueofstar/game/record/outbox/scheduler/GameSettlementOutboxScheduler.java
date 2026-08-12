package com.sang.leagueofstar.game.record.outbox.scheduler;

import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSettlementOutboxScheduler {

    private final GameSettlementOutboxWorker worker;

    @Scheduled(fixedDelayString = GameRecordConstants.OUTBOX_POLLING_FIXED_DELAY_MS)
    public void processPendingOutbox() {
        try {
            worker.processPendingBatch();
        } catch (RuntimeException e) {
            log.warn("Failed to poll game settlement outbox.", e);
        }
    }
}
