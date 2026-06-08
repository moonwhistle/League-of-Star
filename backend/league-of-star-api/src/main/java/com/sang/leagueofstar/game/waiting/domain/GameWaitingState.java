package com.sang.leagueofstar.game.waiting.domain;

/**
 * Redis에 저장된 gameRoom waiting 상태입니다.
 */
public record GameWaitingState(
        Long gameRoomId,
        Long userAId,
        Long userBId,
        boolean userAReady,
        boolean userBReady,
        long createdAtMillis,
        long deadlineAtMillis
) {

    public boolean bothReady() {
        return userAReady && userBReady;
    }
}
