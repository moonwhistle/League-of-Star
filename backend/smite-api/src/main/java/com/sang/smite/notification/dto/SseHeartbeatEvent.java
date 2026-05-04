package com.sang.smite.notification.dto;

import java.time.Instant;

public record SseHeartbeatEvent(
        Instant sentAt
) {
}
