package com.sang.leagueofstar.game.end.service;

import com.sang.leagueofstar.domain.game.service.GameNaturalDeathSettlementService;
import com.sang.leagueofstar.domain.game.service.dto.GameNaturalDeathSettlementResult;
import com.sang.leagueofstar.game.end.common.constant.GameEndConstants;
import com.sang.leagueofstar.game.result.dto.GameResultPayload;
import com.sang.leagueofstar.game.result.service.GameResultPayloadFactory;
import com.sang.leagueofstar.game.result.service.GameResultWebSocketSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameEndSettlementService {

    private final GameEndScheduleService gameEndScheduleService;
    private final GameNaturalDeathSettlementService gameNaturalDeathSettlementService;
    private final GameResultPayloadFactory gameResultPayloadFactory;
    private final GameResultWebSocketSender gameResultWebSocketSender;
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
        if (result.status().isFinished()) {
            broadcastNaturalDeathResult(gameRoomId, nowMillis, result);
        }
        if (result.shouldCleanupEndDeadline()) {
            cleanupEndDeadline(gameRoomId);
        }
    }

    private void broadcastNaturalDeathResult(Long gameRoomId,
                                             long finishedAtMillis,
                                             GameNaturalDeathSettlementResult result) {
        try {
            GameResultPayload payload = createNaturalDeathResultPayload(gameRoomId, finishedAtMillis, result);
            gameResultWebSocketSender.broadcastGameResult(gameRoomId, payload);
        } catch (IOException e) {
            log.warn("Failed to broadcast natural death GAME_RESULT: gameRoomId={}", gameRoomId, e);
        }
    }

    private GameResultPayload createNaturalDeathResultPayload(Long gameRoomId,
                                                              long finishedAtMillis,
                                                              GameNaturalDeathSettlementResult result) {
        if (result.finishedGameRoom().isPracticeMode()) {
            return gameResultPayloadFactory.practiceTimeout(
                    gameRoomId,
                    result.finishedGameRoom().getResult(),
                    result.finishedGameRoom().getWinnerId(),
                    finishedAtMillis,
                    result.actions()
            );
        }
        return gameResultPayloadFactory.naturalDeathDraw(
                gameRoomId,
                result.finishedGameRoom(),
                finishedAtMillis,
                result.actions()
        );
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
