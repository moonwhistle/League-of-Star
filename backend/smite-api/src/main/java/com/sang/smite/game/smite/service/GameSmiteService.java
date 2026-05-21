package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.service.GameActionCommandService;
import com.sang.smite.domain.game.service.GameActionReadService;
import com.sang.smite.domain.game.service.GameActionSaveResult;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameSmiteJudgementService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import com.sang.smite.game.smite.dto.GameResultPayload;
import com.sang.smite.game.smite.dto.GameSmiteHandleResponse;
import com.sang.smite.game.smite.dto.SmiteResultPayload;
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
            return existingAction.map(action -> GameSmiteHandleResponse.smiteOnly(
                    SmiteResultPayload.from(action, true)
            ));
        }

        List<GameAction> existingActions = gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                command.gameRoomId()
        );
        return gameSmiteJudgementService
                .judge(gameRoom, command.userId(), command.serverReceiveTimeMs(), existingActions)
                .map(gameActionCommandService::saveIfAbsent)
                .map(saveResult -> toResponse(command.gameRoomId(), saveResult));
    }

    private GameSmiteHandleResponse toResponse(Long gameRoomId, GameActionSaveResult saveResult) {
        SmiteResultPayload smiteResult = SmiteResultPayload.from(saveResult.action(), saveResult.idempotent());
        if (saveResult.action().getDragonHpAtSmite() > GameRules.SMITE_DAMAGE) {
            return nonKillResponse(gameRoomId, smiteResult);
        }

        Optional<GameRoom> finishedGameRoom = gameRoomCommandService.finishInProgressRoomBySmiteKill(
                gameRoomId,
                saveResult.action().getUserId()
        );
        return finishedGameRoom.map(gameRoom -> GameSmiteHandleResponse.withGameResult(
                smiteResult,
                smiteKillGameResult(gameRoomId, gameRoom)
        )).orElseGet(() -> GameSmiteHandleResponse.smiteOnly(smiteResult));

    }

    private GameSmiteHandleResponse nonKillResponse(Long gameRoomId,
                                                    SmiteResultPayload smiteResult) {
        List<GameAction> currentActions = gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                gameRoomId
        );
        if (!bothUsersUsedSmiteWithoutKill(currentActions)) {
            return GameSmiteHandleResponse.smiteOnly(smiteResult);
        }

        Optional<GameRoom> finishedGameRoom = gameRoomCommandService.finishInProgressRoomByBothSmitesUsedDraw(
                gameRoomId
        );
        return finishedGameRoom.map(gameRoom -> GameSmiteHandleResponse.withGameResult(
                smiteResult,
                bothSmitesUsedDrawGameResult(gameRoomId, gameRoom, currentActions)
        )).orElseGet(() -> GameSmiteHandleResponse.smiteOnly(smiteResult));

    }

    private boolean bothUsersUsedSmiteWithoutKill(List<GameAction> actions) {
        return actions.stream()
                .map(GameAction::getUserId)
                .distinct()
                .count() == GameRoom.MAX_PARTICIPANTS
                && actions.stream().noneMatch(GameAction::isKill);
    }

    private GameSmiteHandleResponse currentGameResult(Long gameRoomId, GameRoom gameRoom) {
        return GameSmiteHandleResponse.gameResultOnly(gameResultPayloadFactory.currentResult(
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
