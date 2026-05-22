package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;

import java.util.List;

public record GameNaturalDeathSettlementResult(
        GameNaturalDeathSettlementStatus status,
        Long nextNaturalDeathAtMillis,
        GameRoom finishedGameRoom,
        List<GameAction> actions
) {

    public static GameNaturalDeathSettlementResult finished(GameRoom finishedGameRoom, List<GameAction> actions) {
        return new GameNaturalDeathSettlementResult(
                GameNaturalDeathSettlementStatus.FINISHED,
                null,
                finishedGameRoom,
                List.copyOf(actions)
        );
    }

    public static GameNaturalDeathSettlementResult noOp() {
        return new GameNaturalDeathSettlementResult(GameNaturalDeathSettlementStatus.NO_OP, null, null, List.of());
    }

    public static GameNaturalDeathSettlementResult rescheduled(long nextNaturalDeathAtMillis) {
        return new GameNaturalDeathSettlementResult(
                GameNaturalDeathSettlementStatus.RESCHEDULED,
                nextNaturalDeathAtMillis,
                null,
                List.of()
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
