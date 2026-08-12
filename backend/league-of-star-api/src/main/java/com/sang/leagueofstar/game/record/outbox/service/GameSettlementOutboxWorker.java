package com.sang.leagueofstar.game.record.outbox.service;

import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameSettlementOutboxWorker {

    private final GameSettlementOutboxClaimService claimService;
    private final GameSettlementOutboxProcessor processor;

    public void processImmediately(String eventId) {
        claimService.claim(eventId).ifPresent(processor::process);
    }

    public int processPendingBatch() {
        List<ClaimedGameSettlementOutbox> claimed = claimService.claimBatch(
                GameRecordConstants.OUTBOX_PROCESS_BATCH_SIZE
        );
        claimed.forEach(processor::process);
        return claimed.size();
    }
}
