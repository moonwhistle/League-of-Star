package com.sang.leagueofstar.game.waiting.pubsub;

import com.sang.leagueofstar.game.waiting.common.constant.GameWaitingConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * game waiting timeout Redis Pub/Sub 구독 설정입니다.
 */
@Configuration
public class GameWaitingTimeoutPubSubConfig {

    @Bean
    public RedisMessageListenerContainer gameWaitingTimeoutRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            GameWaitingTimeoutPubSubSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                subscriber,
                new ChannelTopic(GameWaitingConstants.WAITING_TIMEOUT_CHANNEL)
        );
        return container;
    }
}
