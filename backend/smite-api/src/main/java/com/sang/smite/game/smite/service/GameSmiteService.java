package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.service.GameActionCommandService;
import com.sang.smite.domain.game.service.GameActionReadService;
import com.sang.smite.domain.game.service.GameActionSaveResult;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameSmiteJudgementService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import com.sang.smite.game.smite.dto.SmiteResultPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameSmiteService {

    private final GameRoomCommandService gameRoomCommandService;
    private final GameActionReadService gameActionReadService;
    private final GameActionCommandService gameActionCommandService;
    private final GameSmiteJudgementService gameSmiteJudgementService;

    @Transactional
    public Optional<SmiteResultPayload> handleSmite(GameSmiteCommand command) {
        GameRoom gameRoom = gameRoomCommandService.lockInProgressRoomForSmite(command.gameRoomId(), command.userId());

        Optional<GameAction> existingAction = gameActionReadService.findByGameRoomIdAndUserId(
                command.gameRoomId(),
                command.userId()
        );
        if (existingAction.isPresent()) {
            return existingAction.map(action -> SmiteResultPayload.from(action, true));
        }

        List<GameAction> existingActions = gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                command.gameRoomId()
        );
        return gameSmiteJudgementService
                .judge(gameRoom, command.userId(), command.serverReceiveTimeMs(), existingActions)
                .map(gameActionCommandService::saveIfAbsent)
                .map(this::toPayload);
    }

    private SmiteResultPayload toPayload(GameActionSaveResult saveResult) {
        return SmiteResultPayload.from(saveResult.action(), saveResult.idempotent());
    }
}
