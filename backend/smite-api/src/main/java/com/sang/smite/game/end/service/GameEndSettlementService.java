package com.sang.smite.game.end.service;

import com.sang.smite.domain.game.service.GameNaturalDeathSettlementResult;
import com.sang.smite.domain.game.service.GameNaturalDeathSettlementService;
import com.sang.smite.game.end.common.constant.GameEndConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameEndSettlementService {

    private final GameEndScheduleService gameEndScheduleService;
    private final GameNaturalDeathSettlementService gameNaturalDeathSettlementService;
    private final Clock clock;

    public void processDueEndDeadlines() {
        long nowMillis = clock.millis();
        List<Long> dueGameRoomIds = gameEndScheduleService.findDueEndDeadlines(
                nowMillis,
                GameEndConstants.END_DEADLINE_CANDIDATE_BATCH_SIZE
        );

        for (Long gameRoomId : dueGameRoomIds) {
            processGameRoom(gameRoomId, nowMillis);
        }
    }

    private void processGameRoom(Long gameRoomId, long nowMillis) {
        try {
            GameNaturalDeathSettlementResult result = gameNaturalDeathSettlementService.settle(gameRoomId, nowMillis);
            handleSettlementResult(gameRoomId, nowMillis, result);
        } catch (RuntimeException e) {
            log.warn("Failed to process game end settlement: gameRoomId={}", gameRoomId, e);
        }
    }

    private void handleSettlementResult(Long gameRoomId, long nowMillis, GameNaturalDeathSettlementResult result) {
        if (result.shouldRescheduleEndDeadline()) {
            updateEndDeadlineIfDue(gameRoomId, nowMillis, result.nextNaturalDeathAtMillis());
            return;
        }
        if (result.shouldCleanupEndDeadline()) {
            cleanupEndDeadline(gameRoomId);
        }
    }

    private void updateEndDeadlineIfDue(Long gameRoomId, long nowMillis, long naturalDeathAtMillis) {
        try {
            gameEndScheduleService.updateEndDeadlineIfDue(gameRoomId, nowMillis, naturalDeathAtMillis);
        } catch (RuntimeException e) {
            log.warn("Failed to update game end deadline: gameRoomId={}, nowMillis={}, naturalDeathAtMillis={}",
                    gameRoomId, nowMillis, naturalDeathAtMillis, e);
        }
    }

    private void cleanupEndDeadline(Long gameRoomId) {
        try {
            gameEndScheduleService.cleanupEndDeadline(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup game end deadline: gameRoomId={}", gameRoomId, e);
        }
    }
}
