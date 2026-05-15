package com.sang.smite.game.websocket.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameWebSocketServerMessageTest {

    private static final Long USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("playerJoined - PLAYER_JOINED 메시지를 생성한다")
    void playerJoined() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.playerJoined(USER_ID);

        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.PLAYER_JOINED);
        assertThat(result.payload()).isEqualTo(new GameWebSocketServerMessage.PlayerPayload(USER_ID));
    }

    @Test
    @DisplayName("playerReady - PLAYER_READY 메시지를 생성한다")
    void playerReady() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.playerReady(USER_ID, true);

        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.PLAYER_READY);
        assertThat(result.payload()).isEqualTo(new GameWebSocketServerMessage.PlayerReadyPayload(USER_ID, true));
    }

    @Test
    @DisplayName("playerLeft - PLAYER_LEFT 메시지를 생성한다")
    void playerLeft() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.playerLeft(USER_ID);

        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.PLAYER_LEFT);
        assertThat(result.payload()).isEqualTo(new GameWebSocketServerMessage.PlayerPayload(USER_ID));
    }

    @Test
    @DisplayName("invalidMessageType - ERROR 메시지를 생성한다")
    void invalidMessageType() throws Exception {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.invalidMessageType();

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.ERROR.name());
        assertThat(json.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
    }
}
