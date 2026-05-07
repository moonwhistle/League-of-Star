package com.sang.smite.notification.service;

import com.sang.smite.notification.constants.MatchNotificationEventName;
import com.sang.smite.notification.domain.SseConnection;
import com.sang.smite.notification.dto.SseHeartbeatEvent;
import com.sang.smite.notification.metrics.SseNotificationMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseNotificationSenderTest {

    private final SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());
    private final SseNotificationSender sender = new SseNotificationSender(registry, SseNotificationMetrics.noop());

    @Test
    @DisplayName("SSE 이벤트 전송에 성공하면 true를 반환하고 연결을 유지한다.")
    void sendEvent() throws IOException {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connection = new SseConnection(1L, emitter);
        registry.register(connection);

        // when
        boolean result = sender.send(
                connection,
                MatchNotificationEventName.HEARTBEAT,
                new SseHeartbeatEvent(Instant.parse("2026-05-04T00:00:00Z"))
        );

        // then
        assertThat(result).isTrue();
        assertThat(registry.findByUserId(1L)).contains(connection);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("SSE 이벤트 전송에 실패하면 false를 반환하고 실패 연결을 제거한다.")
    void removeConnectionWhenSendFails() throws IOException {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        IOException exception = new IOException("disconnected");
        SseConnection connection = new SseConnection(1L, emitter);
        doThrow(exception).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        registry.register(connection);

        // when
        boolean result = sender.send(
                connection,
                MatchNotificationEventName.HEARTBEAT,
                new SseHeartbeatEvent(Instant.parse("2026-05-04T00:00:00Z"))
        );

        // then
        assertThat(result).isFalse();
        assertThat(registry.findByUserId(1L)).isEmpty();
        verify(emitter).completeWithError(exception);
    }
}
