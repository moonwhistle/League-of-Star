package com.sang.leagueofstar.game.websocket.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameWebSocketMessageTypeTest {

    @Test
    @DisplayName("CLIENT_READY, RTT_PONG, SMITE는 client message type이다")
    void clientMessages_AreClientMessage() {
        assertThat(GameWebSocketMessageType.CLIENT_READY.isClientMessage()).isTrue();
        assertThat(GameWebSocketMessageType.CLIENT_READY.isServerMessage()).isFalse();
        assertThat(GameWebSocketMessageType.RTT_PONG.isClientMessage()).isTrue();
        assertThat(GameWebSocketMessageType.RTT_PONG.isServerMessage()).isFalse();
        assertThat(GameWebSocketMessageType.SMITE.isClientMessage()).isTrue();
        assertThat(GameWebSocketMessageType.SMITE.isServerMessage()).isFalse();
    }

    @Test
    @DisplayName("서버 전송 메시지 type은 server message type이다")
    void serverMessages_AreServerMessage() {
        assertThat(GameWebSocketMessageType.PLAYER_JOINED.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.PLAYER_READY.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.PLAYER_LEFT.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.RTT_PING.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.GAME_WAITING_TIMEOUT.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.GAME_START_FAILED.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.COUNTDOWN.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.GAME_START.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.GAME_RESULT.isServerMessage()).isTrue();
        assertThat(GameWebSocketMessageType.ERROR.isServerMessage()).isTrue();
    }
}
