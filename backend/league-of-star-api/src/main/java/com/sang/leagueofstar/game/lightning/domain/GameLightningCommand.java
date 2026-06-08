package com.sang.leagueofstar.game.lightning.domain;

public record GameLightningCommand(
        Long gameRoomId,
        Long userId,
        long serverReceiveTimeMs
) {
}
