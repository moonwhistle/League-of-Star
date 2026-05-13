package com.sang.smite.notification.match.service;

import com.sang.smite.notification.match.constants.MatchNotificationEventName;
import com.sang.smite.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import com.sang.smite.notification.sse.metrics.SseNotificationMetrics;
import com.sang.smite.notification.sse.connection.SseConnectionRegistry;
import com.sang.smite.notification.sse.sender.SseEventSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * match_response_result Pub/Sub 메시지를 현재 인스턴스의 SSE 연결에 전송합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchResponseResultSseSender {

    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseEventSender sseEventSender;
    private final SseNotificationMetrics sseNotificationMetrics;

    public void send(MatchResponseResultPubSubMessage message) {
        Long targetUserId = message.targetUserId();
        sseConnectionRegistry.findByUserId(targetUserId)
                .ifPresentOrElse(
                        connection -> {
                            sseNotificationMetrics.incrementDispatchLocalHit(MatchNotificationEventName.MATCH_RESPONSE_RESULT);
                            boolean sent = sseEventSender.send(
                                    connection,
                                    MatchNotificationEventName.MATCH_RESPONSE_RESULT,
                                    message.notification()
                            );

                            if (sent) {
                                log.info("[MatchNotification] match_response_result 전송 완료. userId={}, matchId={}",
                                        targetUserId, message.notification().matchId());
                            }
                        },
                        () -> {
                            sseNotificationMetrics.incrementDispatchLocalMiss(MatchNotificationEventName.MATCH_RESPONSE_RESULT);
                            log.info("[MatchNotification] 현재 인스턴스에 SSE 연결이 없어 match_response_result 전송을 스킵합니다. userId={}, matchId={}",
                                    targetUserId, message.notification().matchId());
                        }
                );
    }
}
