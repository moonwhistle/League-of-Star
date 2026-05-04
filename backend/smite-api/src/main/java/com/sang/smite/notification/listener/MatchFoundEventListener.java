package com.sang.smite.notification.listener;

import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.notification.constants.MatchNotificationEventName;
import com.sang.smite.notification.dto.MatchFoundNotification;
import com.sang.smite.notification.service.SseConnectionRegistry;
import com.sang.smite.notification.service.SseNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundEventListener {

    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseNotificationSender sseNotificationSender;
    private final Clock clock = Clock.systemUTC();

    @EventListener
    public void handle(MatchFoundEvent event) {
        Instant eventCreatedAt = Instant.now(clock);

        sendMatchFound(
                event.userA(),
                event.userB(),
                new MatchFoundNotification(
                        event.matchId(),
                        event.userA(),
                        event.userB(),
                        event.acceptTimeoutSeconds(),
                        eventCreatedAt
                )
        );

        sendMatchFound(
                event.userB(),
                event.userA(),
                new MatchFoundNotification(
                        event.matchId(),
                        event.userB(),
                        event.userA(),
                        event.acceptTimeoutSeconds(),
                        eventCreatedAt
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
                        () -> log.info("[MatchNotification] SSE 연결이 없어 match_found 전송을 스킵합니다. userId={}, matchId={}",
                                targetUserId, notification.matchId())
                );
    }
}
