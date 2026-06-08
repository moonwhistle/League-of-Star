package com.sang.leagueofstar.notification.match.pubsub;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationChannelName;
import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import com.sang.leagueofstar.notification.match.dto.MatchResponseAction;
import com.sang.leagueofstar.notification.match.dto.MatchResponseOutcome;
import com.sang.leagueofstar.notification.match.dto.MatchResponseReason;
import com.sang.leagueofstar.notification.match.dto.MatchResponseResultNotification;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import com.sang.leagueofstar.notification.match.pubsub.util.MatchResponseResultPubSubMessageCodec;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetricNames;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchResponseResultPubSubPublisherTest {

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final MatchResponseResultPubSubMessageCodec messageCodec = mock(MatchResponseResultPubSubMessageCodec.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SseNotificationMetrics metrics = new SseNotificationMetrics(meterRegistry);
    private final MatchResponseResultPubSubPublisher publisher = new MatchResponseResultPubSubPublisher(
            stringRedisTemplate,
            messageCodec,
            metrics
    );

    @Test
    @DisplayName("match_response_result 메시지를 Redis Pub/Sub channel로 publish한다")
    void publish() {
        MatchResponseResultPubSubMessage message = message();
        when(messageCodec.encode(message)).thenReturn("{\"targetUserId\":1}");

        publisher.publish(message);

        verify(stringRedisTemplate).convertAndSend(
                MatchNotificationChannelName.MATCH_RESPONSE_RESULT,
                "{\"targetUserId\":1}"
        );
        assertThatPublishSuccessCounter(1.0);
    }

    @Test
    @DisplayName("match_response_result publish 실패는 예외를 밖으로 던지지 않고 실패 메트릭으로 격리한다")
    void publishFailure() {
        MatchResponseResultPubSubMessage message = message();
        when(messageCodec.encode(message)).thenReturn("{\"targetUserId\":1}");
        doThrow(new RuntimeException("redis down"))
                .when(stringRedisTemplate)
                .convertAndSend(MatchNotificationChannelName.MATCH_RESPONSE_RESULT, "{\"targetUserId\":1}");

        publisher.publish(message);

        verify(stringRedisTemplate).convertAndSend(
                MatchNotificationChannelName.MATCH_RESPONSE_RESULT,
                "{\"targetUserId\":1}"
        );
        assertThatPublishFailureCounter(1.0);
    }

    private MatchResponseResultPubSubMessage message() {
        return new MatchResponseResultPubSubMessage(
                1L,
                new MatchResponseResultNotification(
                        "match-1",
                        MatchResponseOutcome.MATCHED,
                        MatchResponseReason.BOTH_ACCEPTED,
                        MatchResponseAction.GO_TO_GAME_WAITING,
                        new MatchResponseResultNotification.Opponent(2L, "opponent", "GOLD_IV", 13),
                        null
                )
        );
    }

    private void assertThatPublishSuccessCounter(double expected) {
        assertThat(meterRegistry.get(SseNotificationMetricNames.PUBSUB_PUBLISH_SUCCESS)
                .tag(SseNotificationMetricNames.TAG_EVENT, MatchNotificationEventName.MATCH_RESPONSE_RESULT)
                .counter()
                .count()).isEqualTo(expected);
    }

    private void assertThatPublishFailureCounter(double expected) {
        assertThat(meterRegistry.get(SseNotificationMetricNames.PUBSUB_PUBLISH_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, MatchNotificationEventName.MATCH_RESPONSE_RESULT)
                .counter()
                .count()).isEqualTo(expected);
    }
}
