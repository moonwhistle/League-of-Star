package com.sang.leagueofstar.notification.match.adapter;

import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.matching.domain.event.MatchResponseResultEvent;
import com.sang.leagueofstar.matching.domain.event.MatchResponseResultEventPublisher;
import com.sang.leagueofstar.notification.match.factory.MatchResponseResultNotificationFactory;
import com.sang.leagueofstar.notification.match.pubsub.MatchResponseResultPubSubPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * matching 모듈의 매칭 응답 결과 발행 port를 Redis Pub/Sub 알림으로 연결합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchResponseResultEventPublisherAdapter implements MatchResponseResultEventPublisher {

    private final MatchResponseResultPubSubPublisher publisher;
    private final MatchResponseResultNotificationFactory notificationFactory;

    @Override
    public void publish(MatchResponseResultEvent event) {
        if (!isPublishable(event)) {
            return;
        }

        publisher.publish(notificationFactory.createForUserA(event));
        publisher.publish(notificationFactory.createForUserB(event));
    }

    private boolean isPublishable(MatchResponseResultEvent event) {
        return event.sessionStatus() == MatchStatus.ACCEPTED
                || event.sessionStatus() == MatchStatus.DECLINED
                || event.sessionStatus() == MatchStatus.TIMEOUT
                || event.sessionStatus() == MatchStatus.GAME_SETUP_FAILED;
    }
}
