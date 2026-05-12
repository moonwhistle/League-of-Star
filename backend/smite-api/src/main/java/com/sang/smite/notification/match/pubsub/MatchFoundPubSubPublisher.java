package com.sang.smite.notification.match.pubsub;

import com.sang.smite.notification.match.constants.MatchNotificationChannelName;
import com.sang.smite.notification.match.constants.MatchNotificationEventName;
import com.sang.smite.notification.sse.metrics.SseNotificationMetrics;
import com.sang.smite.notification.match.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.match.pubsub.util.MatchFoundPubSubMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * match_found 메시지를 Redis Pub/Sub channel로 발행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundPubSubPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final MatchFoundPubSubMessageCodec messageCodec;
    private final SseNotificationMetrics metrics;

    public void publish(MatchFoundPubSubMessage message) {
        try {
            String payload = messageCodec.encode(message);
            stringRedisTemplate.convertAndSend(MatchNotificationChannelName.MATCH_FOUND, payload);
            metrics.incrementPubSubPublishSuccess(MatchNotificationEventName.MATCH_FOUND);
            log.info("[MatchNotification] match_found Pub/Sub publish 완료. matchId={}, userA={}, userB={}",
                    message.matchId(), message.userA(), message.userB());
        } catch (Exception e) {
            metrics.incrementPubSubPublishFailure(MatchNotificationEventName.MATCH_FOUND);
            log.error("[MatchNotification] match_found Pub/Sub publish 실패. matchId={}, userA={}, userB={}",
                    message.matchId(), message.userA(), message.userB(), e);
        }
    }
}
