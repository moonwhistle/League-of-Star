package com.sang.smite.game.rtt.domain;

public record GameRttPongResult(
        boolean accepted,
        boolean completed,
        boolean passed,
        int sampleCount
) {

    public static GameRttPongResult rejected() {
        return new GameRttPongResult(false, false, false, 0);
    }

    public static GameRttPongResult recorded(int sampleCount) {
        return new GameRttPongResult(true, false, false, sampleCount);
    }

    public static GameRttPongResult completed(boolean passed, int sampleCount) {
        return new GameRttPongResult(true, true, passed, sampleCount);
    }

    public boolean needsNextPing() {
        return accepted && !completed;
    }

    public int nextSeq() {
        return sampleCount + 1;
    }
}
