package com.sang.smite.game.websocket.dto;

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
}
