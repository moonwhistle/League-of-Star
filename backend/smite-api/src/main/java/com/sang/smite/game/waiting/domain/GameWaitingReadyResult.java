package com.sang.smite.game.waiting.domain;

/**
 * CLIENT_READY를 Redis waiting 상태에 반영한 결과입니다.
 */
public record GameWaitingReadyResult(
        boolean accepted,
        boolean bothReady
) {

    public static GameWaitingReadyResult accepted(boolean bothReady) {
        return new GameWaitingReadyResult(true, bothReady);
    }

    public static GameWaitingReadyResult rejected() {
        return new GameWaitingReadyResult(false, false);
    }
}
