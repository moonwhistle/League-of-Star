package com.sang.smite.game.smite.domain;

public record GameSmiteCommand(
        Long gameRoomId,
        Long userId,
        long serverReceiveTimeMs
) {
}
