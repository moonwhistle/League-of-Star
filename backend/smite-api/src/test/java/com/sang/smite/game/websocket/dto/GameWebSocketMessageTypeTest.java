package com.sang.smite.game.websocket.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameWebSocketMessageTypeTest {

    @Test
    @DisplayName("CLIENT_READY는 client message type이다")
    void clientReady_IsClientMessage() {
        assertThat(GameWebSocketMessageType.CLIENT_READY.isClientMessage()).isTrue();
        assertThat(GameWebSocketMessageType.CLIENT_READY.isServerMessage()).isFalse();
    }

    @Test
    @DisplayName("PLAYER_JOINED, PLAYER_READY, PLAYER_LEFT, ERROR는 server message type이다")
    void serverMessages_AreServerMessage() {
        assertThat(GameWebSocketMessageType.PLAYER_JOINED.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.PLAYER_READY.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.PLAYER_LEFT.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.ERROR.isServerMessage()).isTrue();
    }
}
