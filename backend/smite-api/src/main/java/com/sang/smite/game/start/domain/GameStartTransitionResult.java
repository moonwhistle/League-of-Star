package com.sang.smite.game.start.domain;

public record GameStartTransitionResult(
        boolean started,
        GameStartBlockedReason blockedReason,
        long serverTimeMillis,
        long startAtMillis
) {

    public static GameStartTransitionResult started(long serverTimeMillis, long startAtMillis) {
        return new GameStartTransitionResult(true, null, serverTimeMillis, startAtMillis);
    }

    public static GameStartTransitionResult blocked(GameStartBlockedReason blockedReason) {
        return new GameStartTransitionResult(false, blockedReason, 0L, 0L);
    }
}
