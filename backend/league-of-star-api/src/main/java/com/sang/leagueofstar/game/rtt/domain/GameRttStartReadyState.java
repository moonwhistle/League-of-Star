package com.sang.leagueofstar.game.rtt.domain;

public record GameRttStartReadyState(
        Long gameRoomId,
        Long userAId,
        Long userBId
) {
}
