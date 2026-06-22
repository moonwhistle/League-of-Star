package com.sang.leagueofstar.game.practice.dto;

import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;

public record PracticeGameStartResponse(
        Long gameRoomId,
        long serverTime,
        long startAt,
        String webSocketUrl,
        GameStartScenarioPayload scenario
) {
}
