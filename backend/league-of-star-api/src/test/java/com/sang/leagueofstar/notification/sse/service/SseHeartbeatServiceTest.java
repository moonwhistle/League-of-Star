package com.sang.leagueofstar.notification.sse.service;

import com.sang.leagueofstar.notification.sse.connection.SseConnection;
import com.sang.leagueofstar.notification.sse.connection.SseConnectionRegistry;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import com.sang.leagueofstar.notification.sse.sender.SseEventSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseHeartbeatServiceTest {

    private static final Instant HEARTBEAT_SENT_AT = Instant.parse("2026-05-07T00:00:00Z");

    private final SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());
    private final SseEventSender sender = new SseEventSender(registry, SseNotificationMetrics.noop());
    private final SseHeartbeatService heartbeatService = new SseHeartbeatService(
            registry,
            sender,
            mock(TaskScheduler.class),
            Clock.fixed(HEARTBEAT_SENT_AT, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("등록된 SSE 연결에 heartbeat 이벤트를 전송한다.")
    void sendHeartbeat() throws IOException {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connection = new SseConnection(1L, emitter);
        registry.register(connection);

        // when
        heartbeatService.sendHeartbeat();

        // then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(registry.findByUserId(1L)).contains(connection);
    }

    @Test
    @DisplayName("heartbeat 전송에 실패하면 연결을 제거하고 에러 완료 처리한다.")
    void removeConnectionWhenHeartbeatFails() throws IOException {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        IOException exception = new IOException("disconnected");
        SseConnection connection = new SseConnection(1L, emitter);
        doThrow(exception).when(emitter).send(org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        registry.register(connection);

        // when
        heartbeatService.sendHeartbeat();

        // then
        assertThat(registry.findByUserId(1L)).isEmpty();
        verify(emitter).completeWithError(exception);
    }
}
