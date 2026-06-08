package com.sang.leagueofstar.game.smite.domain;

public record GameSmiteCommand(
        Long gameRoomId,
        Long userId,
        long serverReceiveTimeMs
) {
}
