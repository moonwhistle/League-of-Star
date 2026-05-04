package com.sang.smite.notification.domain;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 유저 1명의 SSE 연결을 표현합니다.
 */
public record SseConnection(
        Long userId,
        SseEmitter emitter
) {

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
