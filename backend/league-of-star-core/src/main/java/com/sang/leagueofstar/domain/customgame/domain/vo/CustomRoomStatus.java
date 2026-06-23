package com.sang.leagueofstar.domain.customgame.domain.vo;

public enum CustomRoomStatus {
    WAITING,
    STARTED,
    CLOSED;

    public boolean isWaiting() {
        return this == WAITING;
    }

    public boolean isStarted() {
        return this == STARTED;
    }

    public boolean isClosed() {
        return this == CLOSED;
    }
}
