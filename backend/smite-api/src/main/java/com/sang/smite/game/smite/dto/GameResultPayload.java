package com.sang.smite.game.smite.dto;

import com.sang.smite.domain.game.domain.vo.GameResult;

import java.util.List;

public record GameResultPayload(
        Long gameRoomId,
        GameResult result,
        Long winnerUserId,
        String reason,
        long finishedAt,
        List<ActionSummary> actions
) {

    public record ActionSummary(
            Long userId,
            long serverReceiveTime,
            int smiteTimeMs,
            int dragonHpAtSmite,
            int damage,
            int afterHp,
            boolean isKill
    ) {
    }
}
