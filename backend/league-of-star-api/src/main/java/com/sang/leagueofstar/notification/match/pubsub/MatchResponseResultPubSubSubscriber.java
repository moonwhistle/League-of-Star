package com.sang.leagueofstar.notification.match.pubsub;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetricNames;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import com.sang.leagueofstar.notification.match.pubsub.util.MatchResponseResultPubSubMessageCodec;
import com.sang.leagueofstar.notification.match.service.MatchResponseResultSseSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub match_response_result 메시지를 수신해 현재 인스턴스의 SSE 연결로 전달합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchResponseResultPubSubSubscriber implements MessageListener {

    private final MatchResponseResultPubSubMessageCodec messageCodec;
    private final MatchResponseResultSseSender sseSender;
    private final SseNotificationMetrics metrics;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        metrics.incrementPubSubMessageReceived(MatchNotificationEventName.MATCH_RESPONSE_RESULT);

        MatchResponseResultPubSubMessage pubSubMessage;

        try {
            pubSubMessage = messageCodec.decode(payload);
        } catch (Exception e) {
            metrics.incrementPubSubMessageFailure(
                    MatchNotificationEventName.MATCH_RESPONSE_RESULT,
                    SseNotificationMetricNames.REASON_DECODE
            );
            log.error("[MatchNotification] match_response_result Pub/Sub 메시지 decode 실패. payload={}", payload, e);
            return;
        }

        try {
            sseSender.send(pubSubMessage);
        } catch (Exception e) {
            metrics.incrementPubSubMessageFailure(
                    MatchNotificationEventName.MATCH_RESPONSE_RESULT,
                    SseNotificationMetricNames.REASON_DISPATCH
            );
            log.error("[MatchNotification] match_response_result Pub/Sub 메시지 SSE 전송 실패. payload={}", payload, e);
        }
    }
}
