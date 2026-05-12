package com.sang.smite.notification.match.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.notification.match.constants.MatchNotificationChannelName;
import com.sang.smite.notification.match.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.match.pubsub.util.MatchFoundPubSubMessageCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MatchFoundPubSubMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final MatchFoundPubSubMessageCodec codec = new MatchFoundPubSubMessageCodec(objectMapper);

    @Test
    @DisplayName("match_found Pub/Sub channel 이름을 정의한다.")
    void channelName() {
        assertThat(MatchNotificationChannelName.MATCH_FOUND).isEqualTo("notification:match_found");
        assertThat(MatchNotificationChannelName.MATCH_RESPONSE_RESULT).isEqualTo("notification:match_response_result");
    }

    @Test
    @DisplayName("MatchFoundEvent에서 Pub/Sub 메시지를 생성한다.")
    void createFromEvent() {
        // given
        MatchFoundEvent event = new MatchFoundEvent("match-1", 1L, 2L, 10);
        Instant eventCreatedAt = Instant.parse("2026-05-07T00:00:00Z");

        // when
        MatchFoundPubSubMessage message = MatchFoundPubSubMessage.from(event, eventCreatedAt);

        // then
        assertThat(message.matchId()).isEqualTo("match-1");
        assertThat(message.userA()).isEqualTo(1L);
        assertThat(message.userB()).isEqualTo(2L);
        assertThat(message.acceptTimeoutSeconds()).isEqualTo(10);
        assertThat(message.eventCreatedAt()).isEqualTo(eventCreatedAt);
    }

    @Test
    @DisplayName("Pub/Sub 메시지를 JSON으로 직렬화하고 다시 역직렬화한다.")
    void encodeAndDecode() {
        // given
        MatchFoundPubSubMessage message = new MatchFoundPubSubMessage(
                "match-1",
                1L,
                2L,
                10,
                Instant.parse("2026-05-07T00:00:00Z")
        );

        // when
        String payload = codec.encode(message);
        MatchFoundPubSubMessage decoded = codec.decode(payload);

        // then
        assertThat(payload).contains("\"matchId\":\"match-1\"");
        assertThat(decoded).isEqualTo(message);
    }
}
