package com.sang.smite.notification.match.service;

import com.sang.smite.notification.match.constants.MatchNotificationEventName;
import com.sang.smite.notification.match.dto.MatchFoundNotification;
import com.sang.smite.notification.match.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.sse.metrics.SseNotificationMetrics;
import com.sang.smite.notification.sse.connection.SseConnectionRegistry;
import com.sang.smite.notification.sse.sender.SseEventSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * match_found Pub/Sub 메시지를 현재 인스턴스의 SSE 연결에 전송합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundSseSender {

    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseEventSender sseEventSender;
    private final SseNotificationMetrics sseNotificationMetrics;

    public void send(MatchFoundPubSubMessage message) {
        sendMatchFound(
                message.userA(),
                message.userB(),
                createNotification(message, message.userA(), message.userB())
        );

        sendMatchFound(
                message.userB(),
                message.userA(),
                createNotification(message, message.userB(), message.userA())
        );
    }

    private MatchFoundNotification createNotification(
            MatchFoundPubSubMessage message,
            Long userId,
            Long opponentUserId
    ) {
        return new MatchFoundNotification(
                message.matchId(),
                userId,
                opponentUserId,
                message.acceptTimeoutSeconds(),
                message.eventCreatedAt()
        );
    }

    private void sendMatchFound(Long targetUserId, Long opponentUserId, MatchFoundNotification notification) {
        sseConnectionRegistry.findByUserId(targetUserId)
                .ifPresentOrElse(
                        connection -> {
                            sseNotificationMetrics.incrementMatchFoundDispatchLocalHit();
                            boolean sent = sseEventSender.send(
                                    connection,
                                    MatchNotificationEventName.MATCH_FOUND,
                                    notification
                            );

                            if (sent) {
                                log.info("[MatchNotification] match_found 전송 완료. userId={}, opponentUserId={}, matchId={}",
                                        targetUserId, opponentUserId, notification.matchId());
                            }
                        },
                        () -> {
                            sseNotificationMetrics.incrementMatchFoundDispatchLocalMiss();
                            log.info("[MatchNotification] 현재 인스턴스에 SSE 연결이 없어 match_found 전송을 스킵합니다. userId={}, matchId={}",
                                    targetUserId, notification.matchId());
                        }
                );
    }
}
