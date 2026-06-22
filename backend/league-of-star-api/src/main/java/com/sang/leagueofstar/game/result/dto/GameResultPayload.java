package com.sang.leagueofstar.game.result.dto;

import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.game.result.domain.PracticeResult;

import java.util.List;

public record GameResultPayload(
        Long gameRoomId,
        GameMode gameMode,
        GameResult result,
        Long winnerUserId,
        String reason,
        PracticeResult practiceResult,
        long finishedAt,
        List<ActionSummary> actions
) {

    public record ActionSummary(
            Long userId,
            long serverReceiveTime,
            int lightningTimeMs,
            int starCoreHpAtLightning,
            int damage,
            int afterHp,
            boolean isKill
    ) {
    }
}
