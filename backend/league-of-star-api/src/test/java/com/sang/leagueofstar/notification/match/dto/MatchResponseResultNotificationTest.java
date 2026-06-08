package com.sang.leagueofstar.notification.match.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
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

    @Test
    @DisplayName("게임 대기 화면 진입 payload를 JSON으로 직렬화한다.")
    void serializeGamePayload() throws JsonProcessingException {
        // given
        MatchResponseResultNotification notification = new MatchResponseResultNotification(
                "match-1",
                MatchResponseOutcome.MATCHED,
                MatchResponseReason.BOTH_ACCEPTED,
                MatchResponseAction.GO_TO_GAME_WAITING,
                new MatchResponseResultNotification.Opponent(2L, "opponent", "GOLD_IV", 13),
                new MatchResponseResultNotification.Game(
                        100L,
                        "/assets/game/star-core-view.mp4",
                        "/ws/game/100"
                )
        );

        // when
        String payload = objectMapper.writeValueAsString(notification);

        // then
        assertThat(payload).contains("\"gameRoomId\":100");
        assertThat(payload).contains("\"videoUrl\":\"/assets/game/star-core-view.mp4\"");
        assertThat(payload).contains("\"webSocketUrl\":\"/ws/game/100\"");
    }
}
