package com.sang.smite.notification.domain;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;

/**
 * 유저 1명의 SSE 연결을 표현합니다.
 */
public record SseConnection(
        Long userId,
        SseEmitter emitter,
        Instant connectedAt
) {

    public SseConnection(Long userId, SseEmitter emitter) {
        this(userId, emitter, Instant.now());
    }

    public void send(String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
    }

    public void complete() {
        emitter.complete();
    }

    public void completeWithError(Throwable throwable) {
        emitter.completeWithError(throwable);
    }
}
