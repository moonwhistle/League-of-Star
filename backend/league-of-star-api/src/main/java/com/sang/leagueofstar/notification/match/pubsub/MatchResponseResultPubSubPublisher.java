package com.sang.leagueofstar.notification.match.pubsub;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationChannelName;
import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import com.sang.leagueofstar.notification.match.pubsub.util.MatchResponseResultPubSubMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * match_response_result 메시지를 Redis Pub/Sub channel로 발행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchResponseResultPubSubPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final MatchResponseResultPubSubMessageCodec messageCodec;
    private final SseNotificationMetrics metrics;

    public void publish(MatchResponseResultPubSubMessage message) {
        try {
            String payload = messageCodec.encode(message);
            stringRedisTemplate.convertAndSend(MatchNotificationChannelName.MATCH_RESPONSE_RESULT, payload);
            metrics.incrementPubSubPublishSuccess(MatchNotificationEventName.MATCH_RESPONSE_RESULT);
            log.info("[MatchNotification] match_response_result Pub/Sub publish 완료. userId={}, matchId={}",
                    message.targetUserId(), message.notification().matchId());
        } catch (Exception e) {
            metrics.incrementPubSubPublishFailure(MatchNotificationEventName.MATCH_RESPONSE_RESULT);
            log.error("[MatchNotification] match_response_result Pub/Sub publish 실패. userId={}, matchId={}",
                    message.targetUserId(), message.notification().matchId(), e);
        }
    }
}
