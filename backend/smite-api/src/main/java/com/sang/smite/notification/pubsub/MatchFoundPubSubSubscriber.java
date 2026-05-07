package com.sang.smite.notification.pubsub;

import com.sang.smite.notification.constants.MatchNotificationEventName;
import com.sang.smite.notification.metrics.SseNotificationMetricNames;
import com.sang.smite.notification.metrics.SseNotificationMetrics;
import com.sang.smite.notification.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.pubsub.util.MatchFoundPubSubMessageCodec;
import com.sang.smite.notification.service.MatchFoundNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub match_found 메시지를 수신해 현재 인스턴스의 SSE 연결로 전달합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundPubSubSubscriber implements MessageListener {

    private final MatchFoundPubSubMessageCodec messageCodec;
    private final MatchFoundNotificationDispatcher dispatcher;
    private final SseNotificationMetrics metrics;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        metrics.incrementPubSubMessageReceived(MatchNotificationEventName.MATCH_FOUND);

        MatchFoundPubSubMessage pubSubMessage;

        try {
            pubSubMessage = messageCodec.decode(payload);
        } catch (Exception e) {
            metrics.incrementPubSubMessageFailure(
                    MatchNotificationEventName.MATCH_FOUND,
                    SseNotificationMetricNames.REASON_DECODE
            );
            log.error("[MatchNotification] match_found Pub/Sub 메시지 decode 실패. payload={}", payload, e);
            return;
        }

        try {
            dispatcher.dispatch(pubSubMessage);
        } catch (Exception e) {
            metrics.incrementPubSubMessageFailure(
                    MatchNotificationEventName.MATCH_FOUND,
                    SseNotificationMetricNames.REASON_DISPATCH
            );
            log.error("[MatchNotification] match_found Pub/Sub 메시지 dispatch 실패. payload={}", payload, e);
        }
    }
}
