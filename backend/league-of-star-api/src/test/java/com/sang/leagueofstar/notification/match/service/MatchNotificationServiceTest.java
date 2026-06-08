package com.sang.leagueofstar.notification.match.service;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import com.sang.leagueofstar.notification.sse.connection.SseConnection;
import com.sang.leagueofstar.notification.sse.dto.SseConnectedEvent;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import com.sang.leagueofstar.notification.sse.connection.SseConnectionRegistry;
import com.sang.leagueofstar.notification.sse.sender.SseEventSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchNotificationServiceTest {

    private static final Instant CONNECTED_AT = Instant.parse("2026-05-07T00:00:00Z");

    private final SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());
    private final SseEventSender sender = mock(SseEventSender.class);
    private final MatchNotificationService service = new MatchNotificationService(
            registry,
            sender,
            Clock.fixed(CONNECTED_AT, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("SSE 연결을 생성하면 유저별 연결을 저장하고 connected 이벤트를 전송한다.")
    void connect() {
        // given
        when(sender.send(any(SseConnection.class), any(String.class), any())).thenReturn(true);

        // when
        SseEmitter emitter = service.connect(1L);

        // then
        assertThat(emitter).isNotNull();
        assertThat(registry.findByUserId(1L)).isPresent();

        ArgumentCaptor<SseConnectedEvent> eventCaptor = ArgumentCaptor.forClass(SseConnectedEvent.class);
        verify(sender).send(
                any(SseConnection.class),
                eq(MatchNotificationEventName.CONNECTED),
                eventCaptor.capture()
        );

        SseConnectedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.connectedAt()).isEqualTo(CONNECTED_AT);
    }
}
