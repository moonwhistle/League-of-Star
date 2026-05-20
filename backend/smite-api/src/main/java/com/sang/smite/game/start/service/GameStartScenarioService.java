package com.sang.smite.game.start.service;

import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.start.dto.GameStartScenarioPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameStartScenarioService {

    private final GameRoomReadService gameRoomReadService;

    public GameStartScenarioPayload getScenarioPayload(Long gameRoomId) {
        return GameStartScenarioPayload.from(gameRoomReadService.getScenarioData(gameRoomId));
    }
}
