package com.sang.leagueofstar.game.start.domain;

import com.sang.leagueofstar.game.rtt.domain.GameRttStartReadyState;

public record GameStartReadyResult(
        boolean ready,
        GameStartBlockedReason blockedReason,
        GameRttStartReadyState rttState
) {

    public static GameStartReadyResult ready(GameRttStartReadyState rttState) {
        return new GameStartReadyResult(true, null, rttState);
    }

    public static GameStartReadyResult blocked(GameStartBlockedReason blockedReason) {
        return new GameStartReadyResult(false, blockedReason, null);
    }
}
