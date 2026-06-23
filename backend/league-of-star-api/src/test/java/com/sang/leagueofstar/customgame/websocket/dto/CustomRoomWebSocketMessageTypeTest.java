package com.sang.leagueofstar.customgame.websocket.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRoomWebSocketMessageTypeTest {

    @Test
    @DisplayName("Custom Room WebSocket server message type을 구분한다")
    void serverMessageTypes() {
        assertThat(CustomRoomWebSocketMessageType.ROOM_UPDATED.isServerMessage()).isTrue();
        assertThat(CustomRoomWebSocketMessageType.ROOM_CLOSED.isServerMessage()).isTrue();
        assertThat(CustomRoomWebSocketMessageType.ERROR.isServerMessage()).isTrue();
    }
}
