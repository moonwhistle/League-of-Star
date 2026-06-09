package com.sang.leagueofstar.game.end.domain;

public record GameEndDeadlineRegistration(
        Long gameRoomId,
        long naturalDeathAtMillis
) {
}
