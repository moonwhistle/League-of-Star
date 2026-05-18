package com.sang.smite.game.waiting.pubsub;

import com.sang.smite.game.waiting.pubsub.dto.GameWaitingTimeoutPubSubMessage;
import com.sang.smite.game.waiting.pubsub.util.GameWaitingTimeoutPubSubMessageCodec;
import com.sang.smite.game.websocket.service.GameWaitingTimeoutWebSocketSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameWaitingTimeoutPubSubSubscriberTest {

    private static final String PAYLOAD = "{\"gameRoomId\":100}";

    private final GameWaitingTimeoutPubSubMessageCodec messageCodec = mock(GameWaitingTimeoutPubSubMessageCodec.class);
    private final GameWaitingTimeoutWebSocketSender webSocketSender = mock(GameWaitingTimeoutWebSocketSender.class);
    private final GameWaitingTimeoutPubSubSubscriber subscriber =
            new GameWaitingTimeoutPubSubSubscriber(messageCodec, webSocketSender);

    @Test
    @DisplayName("Pub/Sub 메시지를 수신하면 decode 후 WebSocket sender에 위임한다")
    void onMessage() {
        // given
        GameWaitingTimeoutPubSubMessage message = new GameWaitingTimeoutPubSubMessage(
                100L,
                "WAITING_TIMEOUT",
                "GO_TO_MATCH_START"
        );
        when(messageCodec.decode(PAYLOAD)).thenReturn(message);

        // when
        subscriber.onMessage(redisMessage(PAYLOAD), null);

        // then
        verify(webSocketSender).sendTimeout(100L, "WAITING_TIMEOUT", "GO_TO_MATCH_START");
    }

    @Test
    @DisplayName("decode 실패는 WebSocket sender에 위임하지 않는다")
    void onDecodeFailure() {
        // given
        doThrow(new IllegalStateException("invalid payload")).when(messageCodec).decode("invalid");

        // when
        subscriber.onMessage(redisMessage("invalid"), null);

        // then
        verify(webSocketSender, never()).sendTimeout(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private DefaultMessage redisMessage(String payload) {
        return new DefaultMessage(
                "game_waiting_timeout".getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }
}
