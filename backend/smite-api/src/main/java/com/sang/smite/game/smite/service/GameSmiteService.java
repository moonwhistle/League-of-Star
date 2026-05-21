package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.service.GameActionReadService;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import com.sang.smite.game.smite.dto.SmiteResultPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameSmiteService {

    private final GameRoomCommandService gameRoomCommandService;
    private final GameActionReadService gameActionReadService;

    @Transactional
    public Optional<SmiteResultPayload> handleSmite(GameSmiteCommand command) {
        gameRoomCommandService.lockInProgressRoomForSmite(command.gameRoomId(), command.userId());

        return gameActionReadService
                .findByGameRoomIdAndUserId(command.gameRoomId(), command.userId())
                .map(action -> SmiteResultPayload.from(action, true));
    }
}
