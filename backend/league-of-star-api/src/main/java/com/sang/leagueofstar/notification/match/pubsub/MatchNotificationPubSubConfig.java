package com.sang.leagueofstar.notification.match.pubsub;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationChannelName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 매칭 알림 Redis Pub/Sub 구독 설정입니다.
 */
@Configuration
public class MatchNotificationPubSubConfig {

    @Bean
    public RedisMessageListenerContainer matchNotificationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MatchFoundPubSubSubscriber matchFoundPubSubSubscriber,
            MatchResponseResultPubSubSubscriber matchResponseResultPubSubSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                matchFoundPubSubSubscriber,
                new ChannelTopic(MatchNotificationChannelName.MATCH_FOUND)
        );
        container.addMessageListener(
                matchResponseResultPubSubSubscriber,
                new ChannelTopic(MatchNotificationChannelName.MATCH_RESPONSE_RESULT)
        );
        return container;
    }
}
