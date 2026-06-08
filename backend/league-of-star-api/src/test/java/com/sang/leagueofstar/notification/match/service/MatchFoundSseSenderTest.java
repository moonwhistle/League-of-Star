package com.sang.leagueofstar.notification.match.service;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import com.sang.leagueofstar.notification.match.dto.MatchFoundNotification;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.leagueofstar.notification.sse.connection.SseConnection;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetricNames;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import com.sang.leagueofstar.notification.sse.connection.SseConnectionRegistry;
import com.sang.leagueofstar.notification.sse.sender.SseEventSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchFoundSseSenderTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SseNotificationMetrics metrics = new SseNotificationMetrics(meterRegistry);
    private final SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());
    private final SseEventSender sender = mock(SseEventSender.class);
    private final MatchFoundSseSender sseSender = new MatchFoundSseSender(registry, sender, metrics);

    @Test
    @DisplayName("연결된 두 유저에게 match_found 이벤트를 전송한다.")
    void sendToConnectedUsers() {
        // given
        SseConnection userAConnection = new SseConnection(1L, mock(SseEmitter.class));
        SseConnection userBConnection = new SseConnection(2L, mock(SseEmitter.class));
        registry.register(userAConnection);
        registry.register(userBConnection);
        when(sender.send(any(SseConnection.class), any(String.class), any())).thenReturn(true);

        // when
        sseSender.send(message());

        // then
        verify(sender).send(eq(userAConnection), eq(MatchNotificationEventName.MATCH_FOUND), any(MatchFoundNotification.class));
        verify(sender).send(eq(userBConnection), eq(MatchNotificationEventName.MATCH_FOUND), any(MatchFoundNotification.class));
        assertThatCounter(SseNotificationMetricNames.MATCH_FOUND_DISPATCH_LOCAL_HITS, 2.0);
    }

    @Test
    @DisplayName("한 유저만 현재 인스턴스에 연결되어 있으면 연결된 유저에게만 전송한다.")
    void sendOnlyConnectedUser() {
        // given
        SseConnection userAConnection = new SseConnection(1L, mock(SseEmitter.class));
        registry.register(userAConnection);
        when(sender.send(any(SseConnection.class), any(String.class), any())).thenReturn(true);

        // when
        sseSender.send(message());

        // then
        ArgumentCaptor<MatchFoundNotification> captor = ArgumentCaptor.forClass(MatchFoundNotification.class);
        verify(sender).send(eq(userAConnection), eq(MatchNotificationEventName.MATCH_FOUND), captor.capture());

        MatchFoundNotification notification = captor.getValue();
        assertThat(notification.matchId()).isEqualTo("match-1");
        assertThat(notification.userId()).isEqualTo(1L);
        assertThat(notification.opponentUserId()).isEqualTo(2L);
        assertThat(notification.acceptTimeoutSeconds()).isEqualTo(10);
        assertThat(notification.eventCreatedAt()).isEqualTo(Instant.parse("2026-05-07T00:00:00Z"));
        assertThatCounter(SseNotificationMetricNames.MATCH_FOUND_DISPATCH_LOCAL_HITS, 1.0);
        assertThatCounter(SseNotificationMetricNames.MATCH_FOUND_DISPATCH_LOCAL_MISSES, 1.0);
    }

    @Test
    @DisplayName("두 유저 모두 현재 인스턴스에 연결되어 있지 않아도 예외 없이 종료한다.")
    void skipWhenNoConnections() {
        sseSender.send(message());

        assertThat(registry.count()).isZero();
        assertThatCounter(SseNotificationMetricNames.MATCH_FOUND_DISPATCH_LOCAL_MISSES, 2.0);
    }

    private MatchFoundPubSubMessage message() {
        return new MatchFoundPubSubMessage(
                "match-1",
                1L,
                2L,
                10,
                Instant.parse("2026-05-07T00:00:00Z")
        );
    }

    private void assertThatCounter(String metricName, double expected) {
        assertThat(meterRegistry.get(metricName).counter().count()).isEqualTo(expected);
    }
}
