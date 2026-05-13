package com.sang.smite.notification.match.pubsub;

import com.sang.smite.notification.match.constants.MatchNotificationChannelName;
import com.sang.smite.notification.sse.metrics.SseNotificationMetrics;
import com.sang.smite.notification.match.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.match.pubsub.util.MatchFoundPubSubMessageCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchFoundPubSubPublisherTest {

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final MatchFoundPubSubMessageCodec messageCodec = mock(MatchFoundPubSubMessageCodec.class);
    private final SseNotificationMetrics metrics = new SseNotificationMetrics(new SimpleMeterRegistry());
    private final MatchFoundPubSubPublisher publisher = new MatchFoundPubSubPublisher(
            stringRedisTemplate,
            messageCodec,
            metrics
    );

    @Test
    @DisplayName("match_found 메시지를 Redis Pub/Sub channel로 publish한다.")
    void publish() {
        // given
        MatchFoundPubSubMessage message = message();
        when(messageCodec.encode(message)).thenReturn("{\"matchId\":\"match-1\"}");

        // when
        publisher.publish(message);

        // then
        verify(stringRedisTemplate).convertAndSend(
                MatchNotificationChannelName.MATCH_FOUND,
                "{\"matchId\":\"match-1\"}"
        );
    }

    @Test
    @DisplayName("publish 실패는 예외를 밖으로 던지지 않고 실패 메트릭으로 격리한다.")
    void publishFailure() {
        // given
        MatchFoundPubSubMessage message = message();
        when(messageCodec.encode(message)).thenReturn("{\"matchId\":\"match-1\"}");
        doThrow(new RuntimeException("redis down"))
                .when(stringRedisTemplate)
                .convertAndSend(MatchNotificationChannelName.MATCH_FOUND, "{\"matchId\":\"match-1\"}");

        // when
        publisher.publish(message);

        // then
        verify(stringRedisTemplate).convertAndSend(
                MatchNotificationChannelName.MATCH_FOUND,
                "{\"matchId\":\"match-1\"}"
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
}
