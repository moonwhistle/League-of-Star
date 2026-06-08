package com.sang.leagueofstar.game.websocket.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameWebSocketClientMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("CLIENT_READY 메시지를 공통 envelope로 역직렬화한다")
    void deserialize_ClientReady() throws Exception {
        // when
        GameWebSocketClientMessage result = objectMapper.readValue(
                "{\"type\":\"CLIENT_READY\",\"payload\":{}}",
                GameWebSocketClientMessage.class
        );

        // then
        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.CLIENT_READY);
        assertThat(result.payload().isObject()).isTrue();
        assertThat(result.isClientReady()).isTrue();
    }

    @Test
    @DisplayName("RTT_PONG 메시지를 공통 envelope로 역직렬화하고 seq를 조회한다")
    void deserialize_RttPong() throws Exception {
        // when
        GameWebSocketClientMessage result = objectMapper.readValue(
                "{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":3,\"userId\":999,\"gameRoomId\":999}}",
                GameWebSocketClientMessage.class
        );

        // then
        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.RTT_PONG);
        assertThat(result.isRttPong()).isTrue();
        assertThat(result.rttSeq()).hasValue(3);
    }

    @Test
    @DisplayName("SMITE 메시지는 시간 payload 없이 공통 envelope로 역직렬화한다")
    void deserialize_Smite() throws Exception {
        // when
        GameWebSocketClientMessage result = objectMapper.readValue(
                "{\"type\":\"SMITE\",\"payload\":{}}",
                GameWebSocketClientMessage.class
        );

        // then
        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.SMITE);
        assertThat(result.payload().isObject()).isTrue();
        assertThat(result.isSmite()).isTrue();
        assertThat(result.payload().has("clientTimestamp")).isFalse();
        assertThat(result.payload().has("serverReceiveTime")).isFalse();
    }

    @Test
    @DisplayName("RTT_PONG payload에 seq가 없으면 빈 값을 반환한다")
    void rttSeq_MissingSeq() throws Exception {
        // when
        GameWebSocketClientMessage result = objectMapper.readValue(
                "{\"type\":\"RTT_PONG\",\"payload\":{}}",
                GameWebSocketClientMessage.class
        );

        // then
        assertThat(result.rttSeq()).isEmpty();
    }
}
