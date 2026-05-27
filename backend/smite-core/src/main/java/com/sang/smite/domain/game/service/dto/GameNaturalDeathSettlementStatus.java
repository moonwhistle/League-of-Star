package com.sang.smite.domain.game.service.dto;

public enum GameNaturalDeathSettlementStatus {
    FINISHED,
    RESCHEDULED,
    NO_OP;

    public boolean isFinished() {
        return this == FINISHED;
    }
}
