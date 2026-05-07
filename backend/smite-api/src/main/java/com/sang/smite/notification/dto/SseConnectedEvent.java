package com.sang.smite.notification.dto;

import java.time.Instant;

public record SseConnectedEvent(
        Long userId,
        Instant connectedAt
) {
}
