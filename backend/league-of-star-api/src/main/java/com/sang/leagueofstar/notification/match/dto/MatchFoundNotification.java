package com.sang.leagueofstar.notification.match.dto;

import java.time.Instant;

public record MatchFoundNotification(
        String matchId,
        Long userId,
        Long opponentUserId,
        int acceptTimeoutSeconds,
        Instant eventCreatedAt
) {
}
