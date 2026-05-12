package com.sang.smite.notification.match.pubsub;

import com.sang.smite.notification.match.constants.MatchNotificationEventName;
import com.sang.smite.notification.match.constants.MatchNotificationChannelName;
import com.sang.smite.notification.sse.metrics.SseNotificationMetricNames;
import com.sang.smite.notification.sse.metrics.SseNotificationMetrics;
import com.sang.smite.notification.match.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.match.pubsub.util.MatchFoundPubSubMessageCodec;
import com.sang.smite.notification.match.service.MatchFoundSseSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchFoundPubSubSubscriberTest {

    private static final String MATCH_FOUND_PAYLOAD = "{\"matchId\":\"match-1\"}";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MatchFoundPubSubMessageCodec messageCodec = mock(MatchFoundPubSubMessageCodec.class);
    private final MatchFoundSseSender sseSender = mock(MatchFoundSseSender.class);
    private final SseNotificationMetrics metrics = new SseNotificationMetrics(meterRegistry);
    private final MatchFoundPubSubSubscriber subscriber = new MatchFoundPubSubSubscriber(messageCodec, sseSender, metrics);

    @Test
    @DisplayName("Pub/Sub 메시지를 수신하면 decode 후 sseSender에 위임한다.")
    void onMessage() {
        // given
        MatchFoundPubSubMessage message = message();
        when(messageCodec.decode(MATCH_FOUND_PAYLOAD)).thenReturn(message);

        // when
        subscriber.onMessage(redisMessage(MATCH_FOUND_PAYLOAD), null);

        // then
        verify(sseSender).send(message);
        assertThatCounter();
    }

    @Test
    @DisplayName("메시지 처리 실패는 예외를 밖으로 던지지 않고 격리한다.")
    void onMessageFailure() {
        // given
        doThrow(new RuntimeException("invalid payload"))
                .when(messageCodec)
                .decode("invalid");

        // when
        subscriber.onMessage(redisMessage("invalid"), null);

        // then
        verify(sseSender, never()).send(org.mockito.ArgumentMatchers.any());
        assertThatCounter();
        assertThatFailureCounter(SseNotificationMetricNames.REASON_DECODE);
    }

    @Test
    @DisplayName("sseSender 처리 실패는 예외를 밖으로 던지지 않고 실패 메트릭으로 격리한다.")
    void onDispatchFailure() {
        // given
        MatchFoundPubSubMessage message = message();
        when(messageCodec.decode(MATCH_FOUND_PAYLOAD)).thenReturn(message);
        doThrow(new RuntimeException("send failed"))
                .when(sseSender)
                .send(message);

        // when
        subscriber.onMessage(redisMessage(MATCH_FOUND_PAYLOAD), null);

        // then
        assertThatCounter();
        assertThatFailureCounter(SseNotificationMetricNames.REASON_DISPATCH);
    }

    private DefaultMessage redisMessage(String payload) {
        return new DefaultMessage(
                MatchNotificationChannelName.MATCH_FOUND.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );
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

    private void assertThatCounter() {
        org.assertj.core.api.Assertions.assertThat(meterRegistry.get(
                        SseNotificationMetricNames.PUBSUB_MESSAGES_RECEIVED)
                .tag(SseNotificationMetricNames.TAG_EVENT, MatchNotificationEventName.MATCH_FOUND)
                .counter()
                .count()).isEqualTo(1.0);
    }

    private void assertThatFailureCounter(String reason) {
        org.assertj.core.api.Assertions.assertThat(meterRegistry.get(SseNotificationMetricNames.PUBSUB_MESSAGES_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, MatchNotificationEventName.MATCH_FOUND)
                .tag(SseNotificationMetricNames.TAG_REASON, reason)
                .counter()
                .count()).isEqualTo(1.0);
    }
}
