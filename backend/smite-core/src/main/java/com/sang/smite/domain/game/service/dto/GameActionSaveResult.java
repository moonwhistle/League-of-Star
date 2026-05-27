package com.sang.smite.domain.game.service.dto;

import com.sang.smite.domain.game.domain.GameAction;

public record GameActionSaveResult(
        GameAction action,
        boolean idempotent
) {

    public static GameActionSaveResult saved(GameAction action) {
        return new GameActionSaveResult(action, false);
    }

    public static GameActionSaveResult idempotent(GameAction action) {
        return new GameActionSaveResult(action, true);
    }
}
