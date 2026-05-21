package com.sang.smite.game.smite.dto;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRules;

public record SmiteResultPayload(
        Long gameRoomId,
        Long userId,
        long serverReceiveTime,
        int smiteTimeMs,
        int dragonHpAtSmite,
        int damage,
        int afterHp,
        boolean isKill,
        boolean idempotent
) {

    public static SmiteResultPayload from(GameAction action, boolean idempotent) {
        return new SmiteResultPayload(
                action.getGameRoomId(),
                action.getUserId(),
                action.getServerReceiveTimeMs(),
                action.getSmiteTimeMs(),
                action.getDragonHpAtSmite(),
                GameRules.SMITE_DAMAGE,
                Math.max(0, action.getDragonHpAtSmite() - GameRules.SMITE_DAMAGE),
                action.isKill(),
                idempotent
        );
    }
}
