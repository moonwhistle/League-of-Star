package com.sang.smite.game.rtt.domain;

public record GameRttState(
        Long gameRoomId,
        Long userAId,
        Long userBId,
        GameRttStatus userAStatus,
        GameRttStatus userBStatus
) {

    public boolean hasFailed() {
        return userAStatus == GameRttStatus.FAILED
                || userBStatus == GameRttStatus.FAILED;
    }
}
