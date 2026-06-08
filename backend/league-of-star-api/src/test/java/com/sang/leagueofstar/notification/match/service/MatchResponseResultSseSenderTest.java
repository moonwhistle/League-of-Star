package com.sang.leagueofstar.notification.match.service;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import com.sang.leagueofstar.notification.match.dto.MatchResponseAction;
import com.sang.leagueofstar.notification.match.dto.MatchResponseOutcome;
import com.sang.leagueofstar.notification.match.dto.MatchResponseReason;
import com.sang.leagueofstar.notification.match.dto.MatchResponseResultNotification;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import com.sang.leagueofstar.notification.sse.connection.SseConnection;
import com.sang.leagueofstar.notification.sse.connection.SseConnectionRegistry;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetricNames;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import com.sang.leagueofstar.notification.sse.sender.SseEventSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MatchResponseResultSseSenderTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SseNotificationMetrics metrics = new SseNotificationMetrics(meterRegistry);
    private final SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());
    private final SseEventSender sender = mock(SseEventSender.class);
    private final MatchResponseResultSseSender sseSender = new MatchResponseResultSseSender(registry, sender, metrics);

    @Test
    @DisplayName("대상 유저가 현재 인스턴스에 연결되어 있으면 match_response_result 이벤트를 전송한다")
    void sendToConnectedUser() {
        SseConnection connection = new SseConnection(1L, mock(SseEmitter.class));
        registry.register(connection);

        sseSender.send(message());

        ArgumentCaptor<MatchResponseResultNotification> captor =
                ArgumentCaptor.forClass(MatchResponseResultNotification.class);
        verify(sender).send(eq(connection), eq(MatchNotificationEventName.MATCH_RESPONSE_RESULT), captor.capture());
        assertThat(captor.getValue().matchId()).isEqualTo("match-1");
        assertThatCounter(SseNotificationMetricNames.DISPATCH_LOCAL_HITS, 1.0);
    }

    @Test
    @DisplayName("대상 유저가 현재 인스턴스에 연결되어 있지 않으면 전송하지 않고 miss 메트릭을 증가시킨다")
    void skipWhenNoConnection() {
        sseSender.send(message());

        verify(sender, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        assertThatCounter(SseNotificationMetricNames.DISPATCH_LOCAL_MISSES, 1.0);
    }

    private MatchResponseResultPubSubMessage message() {
        return new MatchResponseResultPubSubMessage(
                1L,
                new MatchResponseResultNotification(
                        "match-1",
                        MatchResponseOutcome.FAILED,
                        MatchResponseReason.OPPONENT_TIMEOUT,
                        MatchResponseAction.RETURN_TO_MATCHING,
                        new MatchResponseResultNotification.Opponent(2L, "opponent", "GOLD_IV", 13),
                        null
                )
        );
    }

    private void assertThatCounter(String metricName, double expected) {
        assertThat(meterRegistry.get(metricName)
                .tag(SseNotificationMetricNames.TAG_EVENT, MatchNotificationEventName.MATCH_RESPONSE_RESULT)
                .counter()
                .count()).isEqualTo(expected);
    }
}
