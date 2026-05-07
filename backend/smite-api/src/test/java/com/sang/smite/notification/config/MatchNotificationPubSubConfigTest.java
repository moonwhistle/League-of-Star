package com.sang.smite.notification.config;

import com.sang.smite.notification.pubsub.MatchFoundPubSubSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MatchNotificationPubSubConfigTest {

    private final MatchNotificationPubSubConfig config = new MatchNotificationPubSubConfig();

    @Test
    @DisplayName("match_found Pub/Sub listener container를 생성한다.")
    void createListenerContainer() {
        // given
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        MatchFoundPubSubSubscriber subscriber = mock(MatchFoundPubSubSubscriber.class);

        // when
        RedisMessageListenerContainer container =
                config.matchNotificationRedisMessageListenerContainer(connectionFactory, subscriber);

        // then
        assertThat(container).isNotNull();
    }
}
