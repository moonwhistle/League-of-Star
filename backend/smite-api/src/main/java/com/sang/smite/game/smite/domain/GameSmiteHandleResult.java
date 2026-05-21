package com.sang.smite.game.smite.domain;

public record GameSmiteHandleResult(
        boolean accepted,
        boolean gameFinished,
        GameSmiteFailureReason failureReason
) {

    public static GameSmiteHandleResult accepted(boolean gameFinished) {
        return new GameSmiteHandleResult(true, gameFinished, null);
    }

    public static GameSmiteHandleResult failed(GameSmiteFailureReason failureReason) {
        return new GameSmiteHandleResult(false, false, failureReason);
    }
}
