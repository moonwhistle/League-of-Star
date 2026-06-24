package com.sang.leagueofstar.game.lightning.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.GameRules;
import com.sang.leagueofstar.domain.game.service.GameActionCommandService;
import com.sang.leagueofstar.domain.game.service.GameActionReadService;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameLightningJudgementService;
import com.sang.leagueofstar.domain.game.service.dto.GameActionSaveResult;
import com.sang.leagueofstar.game.end.service.GameEndDeadlineAdvanceService;
import com.sang.leagueofstar.game.record.service.GameRecordRankSettlementTrigger;
import com.sang.leagueofstar.game.result.dto.GameResultPayload;
import com.sang.leagueofstar.game.result.service.GameResultPayloadFactory;
import com.sang.leagueofstar.game.lightning.domain.GameLightningCommand;
import com.sang.leagueofstar.game.lightning.dto.GameLightningAppliedPayload;
import com.sang.leagueofstar.game.lightning.dto.GameLightningHandleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameLightningService {

    private final GameRoomCommandService gameRoomCommandService;
    private final GameActionReadService gameActionReadService;
    private final GameActionCommandService gameActionCommandService;
    private final GameLightningJudgementService gameLightningJudgementService;
    private final GameEndDeadlineAdvanceService gameEndDeadlineAdvanceService;
    private final GameRecordRankSettlementTrigger gameRecordRankSettlementTrigger;
    private final GameResultPayloadFactory gameResultPayloadFactory;
    private final Clock clock;

    @Transactional
    public Optional<GameLightningHandleResponse> handleLightning(GameLightningCommand command) {
        GameRoom gameRoom = gameRoomCommandService.lockLightningResultRoom(command.gameRoomId(), command.userId());
        if (gameRoom.getStatus().isFinished()) {
            return Optional.of(currentGameResult(command.gameRoomId(), gameRoom));
        }

        List<GameAction> existingActions = gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                command.gameRoomId()
        );
        if (!isCooldownReady(command.userId(), command.serverReceiveTimeMs(), existingActions)) {
            return Optional.empty();
        }

        return gameLightningJudgementService
                .judge(gameRoom, command.userId(), command.serverReceiveTimeMs(), existingActions)
                .map(gameActionCommandService::save)
                .flatMap(saveResult -> toResponse(command.gameRoomId(), gameRoom, saveResult));
    }

    private Optional<GameLightningHandleResponse> toResponse(Long gameRoomId,
                                                         GameRoom gameRoom,
                                                         GameActionSaveResult saveResult) {
        GameLightningAppliedPayload lightningApplied = toLightningAppliedPayload(gameRoomId, saveResult.action());
        if (saveResult.action().getStarCoreHpAtLightning() > GameRules.LIGHTNING_DAMAGE) {
            gameEndDeadlineAdvanceService.advanceAfterFailedLightning(
                    gameRoom,
                    gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId)
            );
            return Optional.of(GameLightningHandleResponse.applied(lightningApplied));
        }

        Optional<GameRoom> finishedGameRoom = gameRoomCommandService.finishInProgressRoomByLightningKill(
                gameRoomId,
                saveResult.action().getUserId()
        );
        return finishedGameRoom.map(finishedRoom -> {
            if (!finishedRoom.isPracticeMode()) {
                gameRecordRankSettlementTrigger.settleFinishedGameRoomAfterCommit(finishedRoom);
            }
            return GameLightningHandleResponse.appliedAndBroadcastResult(
                    lightningApplied,
                    lightningKillGameResult(gameRoomId, finishedRoom)
            );
        });
    }

    private boolean isCooldownReady(Long userId, long serverReceiveTimeMs, List<GameAction> existingActions) {
        return existingActions.stream()
                .filter(action -> action.getUserId().equals(userId))
                .mapToLong(GameAction::getServerReceiveTimeMs)
                .max()
                .stream()
                .allMatch(lastServerReceiveTimeMs ->
                        serverReceiveTimeMs >= lastServerReceiveTimeMs + GameRules.LIGHTNING_COOLDOWN_MS
                );
    }

    private GameLightningHandleResponse currentGameResult(Long gameRoomId, GameRoom gameRoom) {
        return GameLightningHandleResponse.currentSessionOnly(gameResultPayloadFactory.currentResult(
                gameRoomId,
                gameRoom,
                Instant.now(clock).toEpochMilli(),
                gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId)
        ));
    }

    private GameResultPayload lightningKillGameResult(Long gameRoomId, GameRoom gameRoom) {
        if (gameRoom.isPracticeMode()) {
            return gameResultPayloadFactory.practiceLightningKill(
                    gameRoomId,
                    gameRoom.getResult(),
                    gameRoom.getWinnerId(),
                    Instant.now(clock).toEpochMilli(),
                    gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId)
            );
        }
        return gameResultPayloadFactory.lightningKill(
                gameRoomId,
                gameRoom,
                Instant.now(clock).toEpochMilli(),
                gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId)
        );
    }

    private GameLightningAppliedPayload toLightningAppliedPayload(Long gameRoomId, GameAction action) {
        return new GameLightningAppliedPayload(
                gameRoomId,
                action.getUserId(),
                action.getServerReceiveTimeMs(),
                action.getLightningTimeMs(),
                action.getStarCoreHpAtLightning(),
                GameRules.LIGHTNING_DAMAGE,
                Math.max(0, action.getStarCoreHpAtLightning() - GameRules.LIGHTNING_DAMAGE),
                action.isKill(),
                action.getServerReceiveTimeMs() + GameRules.LIGHTNING_COOLDOWN_MS
        );
    }
}
