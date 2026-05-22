package com.sang.smite.game.end.domain;

public record GameEndDeadlineRegistration(
        Long gameRoomId,
        long naturalDeathAtMillis
) {
}
