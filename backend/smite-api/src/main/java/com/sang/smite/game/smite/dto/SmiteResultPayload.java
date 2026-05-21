package com.sang.smite.game.smite.dto;

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
}
