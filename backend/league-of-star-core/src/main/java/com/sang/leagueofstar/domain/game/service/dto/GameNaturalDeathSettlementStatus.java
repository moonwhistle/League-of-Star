package com.sang.leagueofstar.domain.game.service.dto;

public enum GameNaturalDeathSettlementStatus {
    FINISHED,
    RESCHEDULED,
    NO_OP;

    public boolean isFinished() {
        return this == FINISHED;
    }
}
