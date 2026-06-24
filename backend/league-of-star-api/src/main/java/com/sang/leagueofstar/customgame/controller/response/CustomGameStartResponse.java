package com.sang.leagueofstar.customgame.controller.response;

import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;

public record CustomGameStartResponse(
        Long roomId,
        Long gameRoomId,
        String gameMode,
        long serverTime,
        long startAt,
        String webSocketUrl,
        GameStartScenarioPayload scenario
) {
}
