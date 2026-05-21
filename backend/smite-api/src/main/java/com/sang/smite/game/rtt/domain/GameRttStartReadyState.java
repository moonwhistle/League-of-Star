package com.sang.smite.game.rtt.domain;

public record GameRttStartReadyState(
        Long gameRoomId,
        Long userAId,
        Long userBId
) {
}
