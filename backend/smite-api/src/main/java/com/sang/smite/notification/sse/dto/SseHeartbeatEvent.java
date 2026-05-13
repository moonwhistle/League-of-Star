package com.sang.smite.notification.sse.dto;

import java.time.Instant;

public record SseHeartbeatEvent(
        Instant sentAt
) {
}
