package com.sang.smite.game.rtt.domain;

import java.util.Objects;

public record GameRttStartReadyState(
        Long gameRoomId,
        Long userAId,
        Long userBId,
        long userAMedianRttMs,
        long userBMedianRttMs
) {

    public long medianRttMillis(Long userId) {
        if (Objects.equals(userAId, userId)) {
            return userAMedianRttMs;
        }
        if (Objects.equals(userBId, userId)) {
            return userBMedianRttMs;
        }
        throw new IllegalArgumentException("User is not a participant of gameRoom.");
    }
}
