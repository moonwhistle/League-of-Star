package com.sang.smite.notification.listener;

import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.notification.pubsub.MatchFoundPubSubPublisher;
import com.sang.smite.notification.pubsub.dto.MatchFoundPubSubMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchFoundPubSubPublishListenerTest {

    private final MatchFoundPubSubPublisher publisher = mock(MatchFoundPubSubPublisher.class);
    private final MatchFoundPubSubPublishListener listener = new MatchFoundPubSubPublishListener(publisher);

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
        assertThat(message.eventCreatedAt()).isNotNull();
    }
}
