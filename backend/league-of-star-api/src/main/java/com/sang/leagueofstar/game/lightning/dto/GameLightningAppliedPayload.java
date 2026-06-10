package com.sang.leagueofstar.game.lightning.dto;

public record GameLightningAppliedPayload(
        Long gameRoomId,
        Long userId,
        long serverReceiveTime,
        int lightningTimeMs,
        int starCoreHpAtLightning,
        int damage,
        int afterHp,
        boolean isKill,
        long cooldownUntil
) {
}
