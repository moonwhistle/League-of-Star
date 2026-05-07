package com.sang.smite.notification.service;

import com.sang.smite.notification.constants.MatchNotificationEventName;
import com.sang.smite.notification.dto.MatchFoundNotification;
import com.sang.smite.notification.pubsub.dto.MatchFoundPubSubMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * match_found Pub/Sub 메시지를 현재 인스턴스의 SSE 연결에 전송합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundNotificationDispatcher {

    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseNotificationSender sseNotificationSender;

    public void dispatch(MatchFoundPubSubMessage message) {
        sendMatchFound(
                message.userA(),
                message.userB(),
                new MatchFoundNotification(
                        message.matchId(),
                        message.userA(),
                        message.userB(),
                        message.acceptTimeoutSeconds(),
                        message.eventCreatedAt()
                )
        );

        sendMatchFound(
                message.userB(),
                message.userA(),
                new MatchFoundNotification(
                        message.matchId(),
                        message.userB(),
                        message.userA(),
                        message.acceptTimeoutSeconds(),
                        message.eventCreatedAt()
                )
        );
    }

    private void sendMatchFound(Long targetUserId, Long opponentUserId, MatchFoundNotification notification) {
        sseConnectionRegistry.findByUserId(targetUserId)
                .ifPresentOrElse(
                        connection -> {
                            boolean sent = sseNotificationSender.send(
                                    connection,
                                    MatchNotificationEventName.MATCH_FOUND,
                                    notification
                            );

                            if (sent) {
                                log.info("[MatchNotification] match_found 전송 완료. userId={}, opponentUserId={}, matchId={}",
                                        targetUserId, opponentUserId, notification.matchId());
                            }
                        },
                        () -> log.info("[MatchNotification] 현재 인스턴스에 SSE 연결이 없어 match_found 전송을 스킵합니다. userId={}, matchId={}",
                                targetUserId, notification.matchId())
                );
    }
}
