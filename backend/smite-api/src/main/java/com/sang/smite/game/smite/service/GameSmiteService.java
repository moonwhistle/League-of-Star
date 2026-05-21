package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.service.GameActionReadService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import com.sang.smite.game.smite.dto.SmiteResultPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSmiteService {

    private final GameActionReadService gameActionReadService;

    public Optional<SmiteResultPayload> handleSmite(GameSmiteCommand command) {
        Optional<SmiteResultPayload> existingResult = gameActionReadService
                .findByGameRoomIdAndUserId(command.gameRoomId(), command.userId())
                .map(action -> SmiteResultPayload.from(action, true));
        if (existingResult.isPresent()) {
            return existingResult;
        }

        log.debug("SMITE message received. gameRoomId={}, userId={}, serverReceiveTimeMs={}",
                command.gameRoomId(), command.userId(), command.serverReceiveTimeMs());
        return Optional.empty();
    }
}
