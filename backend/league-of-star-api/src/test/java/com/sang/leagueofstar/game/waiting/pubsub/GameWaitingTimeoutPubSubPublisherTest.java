package com.sang.leagueofstar.game.waiting.pubsub;

import com.sang.leagueofstar.game.waiting.common.constant.GameWaitingConstants;
import com.sang.leagueofstar.game.waiting.pubsub.dto.GameWaitingTimeoutPubSubMessage;
import com.sang.leagueofstar.game.waiting.pubsub.util.GameWaitingTimeoutPubSubMessageCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameWaitingTimeoutPubSubPublisherTest {

    private static final Long GAME_ROOM_ID = 100L;

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final GameWaitingTimeoutPubSubMessageCodec messageCodec = mock(GameWaitingTimeoutPubSubMessageCodec.class);
    private final GameWaitingTimeoutPubSubPublisher publisher = new GameWaitingTimeoutPubSubPublisher(
            stringRedisTemplate,
            messageCodec
    );

    @Test
    @DisplayName("game waiting timeout 메시지를 Redis Pub/Sub channel로 publish한다")
    void publishTimeout() {
        // given
        GameWaitingTimeoutPubSubMessage message = message();
        when(messageCodec.encode(message)).thenReturn("{\"gameRoomId\":100}");

        // when
        publisher.publishTimeout(GAME_ROOM_ID);

        // then
        verify(stringRedisTemplate).convertAndSend(
                GameWaitingConstants.WAITING_TIMEOUT_CHANNEL,
                "{\"gameRoomId\":100}"
        );
    }

    @Test
    @DisplayName("publish 실패는 예외를 던져 scheduler 재시도 대상으로 남긴다")
    void publishTimeout_Failure() {
        // given
        GameWaitingTimeoutPubSubMessage message = message();
        when(messageCodec.encode(message)).thenReturn("{\"gameRoomId\":100}");
        doThrow(new IllegalStateException("redis down"))
                .when(stringRedisTemplate)
                .convertAndSend(GameWaitingConstants.WAITING_TIMEOUT_CHANNEL, "{\"gameRoomId\":100}");

        // when & then
        assertThatThrownBy(() -> publisher.publishTimeout(GAME_ROOM_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    private GameWaitingTimeoutPubSubMessage message() {
        return new GameWaitingTimeoutPubSubMessage(
                GAME_ROOM_ID,
                GameWaitingConstants.WAITING_TIMEOUT_REASON,
                GameWaitingConstants.WAITING_TIMEOUT_ACTION
        );
    }
}
