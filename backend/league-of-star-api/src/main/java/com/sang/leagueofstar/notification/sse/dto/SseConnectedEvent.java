package com.sang.leagueofstar.notification.sse.dto;

import java.time.Instant;

public record SseConnectedEvent(
        Long userId,
        Instant connectedAt
) {
}
