package com.sang.leagueofstar.game.record.outbox.service;

import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.service.FinishedGameMatchStatusCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSettlementOutboxProcessor {

    private final GameRecordRankSettlementService settlementService;
    private final FinishedGameMatchStatusCleanupService cleanupService;
    private final GameSettlementOutboxStateService stateService;

    public void process(ClaimedGameSettlementOutbox claimed) {
        try {
            settlementService.settleFinishedGameRoom(claimed.gameRoomId());
            cleanupService.cleanupSettledGame(claimed.gameRoomId());
            if (!stateService.complete(claimed)) {
                log.warn("Ignored stale outbox completion: eventId={}, gameRoomId={}",
                        claimed.eventId(), claimed.gameRoomId());
            }
        } catch (RuntimeException e) {
            try {
                if (!stateService.fail(claimed, e)) {
                    log.warn("Ignored stale outbox failure: eventId={}, gameRoomId={}",
                            claimed.eventId(), claimed.gameRoomId(), e);
                    return;
                }
            } catch (RuntimeException stateUpdateException) {
                stateUpdateException.addSuppressed(e);
                log.warn("Failed to update game settlement outbox failure state: eventId={}, gameRoomId={}",
                        claimed.eventId(), claimed.gameRoomId(), stateUpdateException);
                return;
            }
            log.warn("Failed to process game settlement outbox: eventId={}, gameRoomId={}, retryCount={}",
                    claimed.eventId(), claimed.gameRoomId(), claimed.retryCount() + 1, e);
        }
    }
}
