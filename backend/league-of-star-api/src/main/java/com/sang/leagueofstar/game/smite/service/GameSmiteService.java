package com.sang.leagueofstar.game.smite.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.GameRules;
import com.sang.leagueofstar.domain.game.service.GameActionCommandService;
import com.sang.leagueofstar.domain.game.service.GameActionReadService;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameSmiteJudgementService;
import com.sang.leagueofstar.domain.game.service.dto.GameActionSaveResult;
import com.sang.leagueofstar.game.end.service.GameEndDeadlineAdvanceService;
import com.sang.leagueofstar.game.record.service.GameRecordRankSettlementTrigger;
import com.sang.leagueofstar.game.result.dto.GameResultPayload;
import com.sang.leagueofstar.game.result.service.GameResultPayloadFactory;
import com.sang.leagueofstar.game.smite.domain.GameSmiteCommand;
import com.sang.leagueofstar.game.smite.dto.GameSmiteHandleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameSmiteService {

    private final GameRoomCommandService gameRoomCommandService;
    private final GameActionReadService gameActionReadService;
    private final GameActionCommandService gameActionCommandService;
    private final GameSmiteJudgementService gameSmiteJudgementService;
    private final GameEndDeadlineAdvanceService gameEndDeadlineAdvanceService;
    private final GameRecordRankSettlementTrigger gameRecordRankSettlementTrigger;
    private final GameResultPayloadFactory gameResultPayloadFactory;
    private final Clock clock;

    @Transactional
    public Optional<GameSmiteHandleResponse> handleSmite(GameSmiteCommand command) {
        GameRoom gameRoom = gameRoomCommandService.lockSmiteResultRoom(command.gameRoomId(), command.userId());
        if (gameRoom.getStatus().isFinished()) {
            return Optional.of(currentGameResult(command.gameRoomId(), gameRoom));
        }

        Optional<GameAction> existingAction = gameActionReadService.findByGameRoomIdAndUserId(
                command.gameRoomId(),
                command.userId()
        );
        if (existingAction.isPresent()) {
            return Optional.empty();
        }

        List<GameAction> existingActions = gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                command.gameRoomId()
        );
        return gameSmiteJudgementService
                .judge(gameRoom, command.userId(), command.serverReceiveTimeMs(), existingActions)
                .map(gameActionCommandService::saveIfAbsent)
                .flatMap(saveResult -> toResponse(command.gameRoomId(), gameRoom, saveResult));
    }

    private Optional<GameSmiteHandleResponse> toResponse(Long gameRoomId,
                                                         GameRoom gameRoom,
                                                         GameActionSaveResult saveResult) {
        if (saveResult.action().getDragonHpAtSmite() > GameRules.SMITE_DAMAGE) {
            return nonKillResponse(gameRoomId, gameRoom);
        }

        Optional<GameRoom> finishedGameRoom = gameRoomCommandService.finishInProgressRoomBySmiteKill(
                gameRoomId,
                saveResult.action().getUserId()
        );
        return finishedGameRoom.map(finishedRoom -> {
            gameRecordRankSettlementTrigger.settleFinishedGameRoomAfterCommit(finishedRoom);
            return GameSmiteHandleResponse.broadcast(smiteKillGameResult(gameRoomId, finishedRoom));
        });
    }

    private Optional<GameSmiteHandleResponse> nonKillResponse(Long gameRoomId, GameRoom gameRoom) {
        List<GameAction> currentActions = gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                gameRoomId
        );
        if (!bothUsersUsedSmiteWithoutKill(currentActions)) {
            gameEndDeadlineAdvanceService.advanceAfterFailedSmite(gameRoom, currentActions);
            return Optional.empty();
        }

        Optional<GameRoom> finishedGameRoom = gameRoomCommandService.finishInProgressRoomByBothSmitesUsedDraw(
                gameRoomId
        );
        return finishedGameRoom.map(finishedRoom -> {
            gameRecordRankSettlementTrigger.settleFinishedGameRoomAfterCommit(finishedRoom);
            return GameSmiteHandleResponse.broadcast(bothSmitesUsedDrawGameResult(
                    gameRoomId,
                    finishedRoom,
                    currentActions
            ));
        });
    }

    private boolean bothUsersUsedSmiteWithoutKill(List<GameAction> actions) {
        return actions.stream()
                .map(GameAction::getUserId)
                .distinct()
                .count() == GameRoom.MAX_PARTICIPANTS
                && actions.stream().noneMatch(GameAction::isKill);
    }

    private GameSmiteHandleResponse currentGameResult(Long gameRoomId, GameRoom gameRoom) {
        return GameSmiteHandleResponse.currentSessionOnly(gameResultPayloadFactory.currentResult(
                gameRoomId,
                gameRoom.getResult(),
                gameRoom.getWinnerId(),
                Instant.now(clock).toEpochMilli(),
                gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId)
        ));
    }

    private GameResultPayload smiteKillGameResult(Long gameRoomId, GameRoom gameRoom) {
        return gameResultPayloadFactory.smiteKill(
                gameRoomId,
                gameRoom.getResult(),
                gameRoom.getWinnerId(),
                Instant.now(clock).toEpochMilli(),
                gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId)
        );
    }

    private GameResultPayload bothSmitesUsedDrawGameResult(Long gameRoomId,
                                                           GameRoom gameRoom,
                                                           List<GameAction> actions) {
        return gameResultPayloadFactory.bothSmitesUsedDraw(
                gameRoomId,
                gameRoom.getResult(),
                gameRoom.getWinnerId(),
                Instant.now(clock).toEpochMilli(),
                actions
        );
    }
}
