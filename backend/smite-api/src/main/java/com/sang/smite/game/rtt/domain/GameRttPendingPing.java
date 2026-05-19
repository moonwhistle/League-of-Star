package com.sang.smite.game.rtt.domain;

public record GameRttPendingPing(
        Long gameRoomId,
        Long userId,
        int seq,
        long sentAtNanos
) {
}
