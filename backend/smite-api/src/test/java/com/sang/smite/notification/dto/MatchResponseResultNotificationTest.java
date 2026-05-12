package com.sang.smite.notification.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.notification.constants.MatchNotificationEventName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchResponseResultNotificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("match_response_result SSE 이벤트 이름을 정의한다.")
    void eventName() {
        assertThat(MatchNotificationEventName.MATCH_RESPONSE_RESULT).isEqualTo("match_response_result");
    }

    @Test
    @DisplayName("매칭 응답 최종 결과 payload를 JSON으로 직렬화한다.")
    void serialize() throws JsonProcessingException {
        // given
        MatchResponseResultNotification notification = new MatchResponseResultNotification(
                "match-1",
                MatchResponseOutcome.FAILED,
                MatchResponseReason.OPPONENT_REJECTED,
                MatchResponseAction.RETURN_TO_MATCHING,
                new MatchResponseResultNotification.Opponent(2L, "opponent", "GOLD_IV", 13),
                null
        );

        // when
        String payload = objectMapper.writeValueAsString(notification);

        // then
        assertThat(payload).contains("\"matchId\":\"match-1\"");
        assertThat(payload).contains("\"outcome\":\"FAILED\"");
        assertThat(payload).contains("\"reason\":\"OPPONENT_REJECTED\"");
        assertThat(payload).contains("\"action\":\"RETURN_TO_MATCHING\"");
        assertThat(payload).contains("\"tier\":\"GOLD_IV\"");
        assertThat(payload).contains("\"tierScore\":13");
    }
}
