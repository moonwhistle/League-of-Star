package com.sang.smite.notification.pubsub;

import com.sang.smite.notification.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.pubsub.util.MatchFoundPubSubMessageCodec;
import com.sang.smite.notification.service.MatchFoundNotificationDispatcher;
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

    private final MatchFoundPubSubMessageCodec messageCodec = mock(MatchFoundPubSubMessageCodec.class);
    private final MatchFoundNotificationDispatcher dispatcher = mock(MatchFoundNotificationDispatcher.class);
    private final MatchFoundPubSubSubscriber subscriber = new MatchFoundPubSubSubscriber(messageCodec, dispatcher);

    @Test
    @DisplayName("Pub/Sub 메시지를 수신하면 decode 후 dispatcher에 위임한다.")
    void onMessage() {
        // given
        MatchFoundPubSubMessage message = message();
        when(messageCodec.decode("{\"matchId\":\"match-1\"}")).thenReturn(message);

        // when
        subscriber.onMessage(redisMessage("{\"matchId\":\"match-1\"}"), null);

        // then
        verify(dispatcher).dispatch(message);
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
        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    private DefaultMessage redisMessage(String payload) {
        return new DefaultMessage(
                "notification:match_found".getBytes(StandardCharsets.UTF_8),
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
}
