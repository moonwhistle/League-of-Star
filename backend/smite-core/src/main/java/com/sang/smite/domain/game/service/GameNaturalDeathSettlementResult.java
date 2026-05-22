package com.sang.smite.domain.game.service;

public record GameNaturalDeathSettlementResult(
        GameNaturalDeathSettlementStatus status,
        Long nextNaturalDeathAtMillis
) {

    public static GameNaturalDeathSettlementResult finished() {
        return new GameNaturalDeathSettlementResult(GameNaturalDeathSettlementStatus.FINISHED, null);
    }

    public static GameNaturalDeathSettlementResult noOp() {
        return new GameNaturalDeathSettlementResult(GameNaturalDeathSettlementStatus.NO_OP, null);
    }

    public static GameNaturalDeathSettlementResult rescheduled(long nextNaturalDeathAtMillis) {
        return new GameNaturalDeathSettlementResult(
                GameNaturalDeathSettlementStatus.RESCHEDULED,
                nextNaturalDeathAtMillis
        );
    }

    public boolean shouldCleanupEndDeadline() {
        return status == GameNaturalDeathSettlementStatus.FINISHED
                || status == GameNaturalDeathSettlementStatus.NO_OP;
    }

    public boolean shouldRescheduleEndDeadline() {
        return status == GameNaturalDeathSettlementStatus.RESCHEDULED;
    }
}
