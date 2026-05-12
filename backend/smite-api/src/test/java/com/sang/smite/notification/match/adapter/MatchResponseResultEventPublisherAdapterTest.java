package com.sang.smite.notification.match.adapter;

import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.matching.domain.event.MatchResponseResultEvent;
import com.sang.smite.notification.match.factory.MatchResponseResultNotificationFactory;
import com.sang.smite.notification.match.pubsub.MatchResponseResultPubSubPublisher;
import com.sang.smite.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchResponseResultEventPublisherAdapterTest {

    private final MatchResponseResultPubSubPublisher publisher = mock(MatchResponseResultPubSubPublisher.class);
    private final MatchResponseResultNotificationFactory notificationFactory =
            mock(MatchResponseResultNotificationFactory.class);
    private final MatchResponseResultEventPublisherAdapter adapter =
            new MatchResponseResultEventPublisherAdapter(publisher, notificationFactory);

    @Test
    @DisplayName("publish 가능한 매칭 응답 결과는 유저별 Pub/Sub 메시지로 발행한다")
    void publish() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                "match-1",
                1L,
                2L,
                10,
                11,
                MatchStatus.ACCEPTED,
                MatchResponseStatus.ACCEPTED,
                MatchResponseStatus.ACCEPTED
        );
        MatchResponseResultPubSubMessage userAMessage = mock(MatchResponseResultPubSubMessage.class);
        MatchResponseResultPubSubMessage userBMessage = mock(MatchResponseResultPubSubMessage.class);
        when(notificationFactory.createForUserA(event)).thenReturn(userAMessage);
        when(notificationFactory.createForUserB(event)).thenReturn(userBMessage);

        adapter.publish(event);

        verify(publisher).publish(userAMessage);
        verify(publisher).publish(userBMessage);
    }

    @Test
    @DisplayName("FOUND 상태 이벤트는 최종 결과가 아니므로 발행하지 않는다")
    void skipFoundEvent() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                "match-1",
                1L,
                2L,
                10,
                11,
                MatchStatus.FOUND,
                MatchResponseStatus.ACCEPTED,
                MatchResponseStatus.PENDING
        );

        adapter.publish(event);

        verify(notificationFactory, never()).createForUserA(event);
        verify(notificationFactory, never()).createForUserB(event);
        verify(publisher, never()).publish(any(MatchResponseResultPubSubMessage.class));
    }
}
