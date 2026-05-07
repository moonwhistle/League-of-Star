package com.sang.smite.notification.listener;

import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.notification.pubsub.MatchFoundPubSubPublisher;
import com.sang.smite.notification.pubsub.dto.MatchFoundPubSubMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 로컬 MatchFoundEvent를 Redis Pub/Sub 메시지로 변환해 전체 API 인스턴스에 전파합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchFoundPubSubPublishListener {

    private final MatchFoundPubSubPublisher publisher;
    private final Clock clock;

    @EventListener
    public void handle(MatchFoundEvent event) {
        publisher.publish(MatchFoundPubSubMessage.from(event, Instant.now(clock)));
    }
}
