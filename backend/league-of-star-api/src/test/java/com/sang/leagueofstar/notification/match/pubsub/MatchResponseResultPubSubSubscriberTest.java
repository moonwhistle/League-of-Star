package com.sang.leagueofstar.notification.match.pubsub;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationChannelName;
import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import com.sang.leagueofstar.notification.match.dto.MatchResponseAction;
import com.sang.leagueofstar.notification.match.dto.MatchResponseOutcome;
import com.sang.leagueofstar.notification.match.dto.MatchResponseReason;
import com.sang.leagueofstar.notification.match.dto.MatchResponseResultNotification;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import com.sang.leagueofstar.notification.match.pubsub.util.MatchResponseResultPubSubMessageCodec;
import com.sang.leagueofstar.notification.match.service.MatchResponseResultSseSender;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetricNames;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchResponseResultPubSubSubscriberTest {

    private static final String PAYLOAD = "{\"targetUserId\":1}";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MatchResponseResultPubSubMessageCodec messageCodec = mock(MatchResponseResultPubSubMessageCodec.class);
    private final MatchResponseResultSseSender sseSender = mock(MatchResponseResultSseSender.class);
    private final SseNotificationMetrics metrics = new SseNotificationMetrics(meterRegistry);
    private final MatchResponseResultPubSubSubscriber subscriber =
            new MatchResponseResultPubSubSubscriber(messageCodec, sseSender, metrics);

    @Test
    @DisplayName("Pub/Sub 메시지를 수신하면 decode 후 match_response_result SSE sender에 위임한다")
    void onMessage() {
        MatchResponseResultPubSubMessage message = message();
        when(messageCodec.decode(PAYLOAD)).thenReturn(message);

        subscriber.onMessage(redisMessage(PAYLOAD), null);

        verify(sseSender).send(message);
        assertThatReceivedCounter(1.0);
    }

    @Test
    @DisplayName("decode 실패는 예외를 밖으로 던지지 않고 실패 메트릭으로 격리한다")
    void onDecodeFailure() {
        doThrow(new RuntimeException("invalid payload")).when(messageCodec).decode("invalid");

        subscriber.onMessage(redisMessage("invalid"), null);

        verify(sseSender, never()).send(org.mockito.ArgumentMatchers.any());
        assertThatReceivedCounter(1.0);
        assertThatFailureCounter(SseNotificationMetricNames.REASON_DECODE, 1.0);
    }

    @Test
    @DisplayName("SSE 전송 위임 실패는 예외를 밖으로 던지지 않고 실패 메트릭으로 격리한다")
    void onDispatchFailure() {
        MatchResponseResultPubSubMessage message = message();
        when(messageCodec.decode(PAYLOAD)).thenReturn(message);
        doThrow(new RuntimeException("send failed")).when(sseSender).send(message);

        subscriber.onMessage(redisMessage(PAYLOAD), null);

        assertThatReceivedCounter(1.0);
        assertThatFailureCounter(SseNotificationMetricNames.REASON_DISPATCH, 1.0);
    }

    private DefaultMessage redisMessage(String payload) {
        return new DefaultMessage(
                MatchNotificationChannelName.MATCH_RESPONSE_RESULT.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );
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

    private void assertThatReceivedCounter(double expected) {
        assertThat(meterRegistry.get(SseNotificationMetricNames.PUBSUB_MESSAGES_RECEIVED)
                .tag(SseNotificationMetricNames.TAG_EVENT, MatchNotificationEventName.MATCH_RESPONSE_RESULT)
                .counter()
                .count()).isEqualTo(expected);
    }

    private void assertThatFailureCounter(String reason, double expected) {
        assertThat(meterRegistry.get(SseNotificationMetricNames.PUBSUB_MESSAGES_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, MatchNotificationEventName.MATCH_RESPONSE_RESULT)
                .tag(SseNotificationMetricNames.TAG_REASON, reason)
                .counter()
                .count()).isEqualTo(expected);
    }
}
