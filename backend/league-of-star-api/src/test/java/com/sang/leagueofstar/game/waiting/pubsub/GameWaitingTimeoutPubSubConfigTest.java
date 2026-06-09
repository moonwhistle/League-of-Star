package com.sang.leagueofstar.game.waiting.pubsub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GameWaitingTimeoutPubSubConfigTest {

    private final GameWaitingTimeoutPubSubConfig config = new GameWaitingTimeoutPubSubConfig();

    @Test
    @DisplayName("game waiting timeout Pub/Sub listener container를 생성한다")
    void createListenerContainer() {
        // given
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        GameWaitingTimeoutPubSubSubscriber subscriber = mock(GameWaitingTimeoutPubSubSubscriber.class);

        // when
        RedisMessageListenerContainer container =
                config.gameWaitingTimeoutRedisMessageListenerContainer(connectionFactory, subscriber);

        // then
        assertThat(container).isNotNull();
    }
}
