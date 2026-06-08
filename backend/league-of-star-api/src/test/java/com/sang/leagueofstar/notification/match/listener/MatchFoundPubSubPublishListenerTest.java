package com.sang.leagueofstar.notification.match.listener;

import com.sang.leagueofstar.domain.match.event.MatchFoundEvent;
import com.sang.leagueofstar.notification.match.pubsub.MatchFoundPubSubPublisher;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchFoundPubSubMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchFoundPubSubPublishListenerTest {

    private static final Instant EVENT_CREATED_AT = Instant.parse("2026-05-07T00:00:00Z");

    private final MatchFoundPubSubPublisher publisher = mock(MatchFoundPubSubPublisher.class);
    private final MatchFoundPubSubPublishListener listener = new MatchFoundPubSubPublishListener(
            publisher,
            Clock.fixed(EVENT_CREATED_AT, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("MatchFoundEvent를 Pub/Sub 메시지로 변환해 publish한다.")
    void publishMatchFoundEvent() {
        // given
        MatchFoundEvent event = new MatchFoundEvent("match-1", 1L, 2L, 10);

        // when
        listener.handle(event);

        // then
        ArgumentCaptor<MatchFoundPubSubMessage> captor = ArgumentCaptor.forClass(MatchFoundPubSubMessage.class);
        verify(publisher).publish(captor.capture());

        MatchFoundPubSubMessage message = captor.getValue();
        assertThat(message.matchId()).isEqualTo("match-1");
        assertThat(message.userA()).isEqualTo(1L);
        assertThat(message.userB()).isEqualTo(2L);
        assertThat(message.acceptTimeoutSeconds()).isEqualTo(10);
        assertThat(message.eventCreatedAt()).isEqualTo(EVENT_CREATED_AT);
    }
}
