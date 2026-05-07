package com.sang.smite.notification.config;

import com.sang.smite.notification.constants.MatchNotificationChannelName;
import com.sang.smite.notification.pubsub.MatchFoundPubSubSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * match_found Redis Pub/Sub 구독 설정입니다.
 */
@Configuration
public class MatchNotificationPubSubConfig {

    @Bean
    public RedisMessageListenerContainer matchNotificationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MatchFoundPubSubSubscriber matchFoundPubSubSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                matchFoundPubSubSubscriber,
                new ChannelTopic(MatchNotificationChannelName.MATCH_FOUND)
        );
        return container;
    }
}
