package com.sang.leagueofstar.game.websocket.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameWebSocketPathResolverTest {

    private final GameWebSocketPathResolver gameWebSocketPathResolver = new GameWebSocketPathResolver();

    @Test
    @DisplayName("resolveGameRoomId - WebSocket path 마지막 segment를 gameRoomId로 변환한다")
    void resolveGameRoomId_Success() {
        // when
        Long result = gameWebSocketPathResolver.resolveGameRoomId(
                URI.create("http://localhost/ws/game/100?token=access-token")
        );

        // then
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("resolveGameRoomId - 마지막 path segment가 없으면 예외를 던진다")
    void resolveGameRoomId_MissingPathSegment_ThrowException() {
        assertThatThrownBy(() -> gameWebSocketPathResolver.resolveGameRoomId(
                URI.create("http://localhost/ws/game/")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolveGameRoomId - 숫자가 아니면 예외를 던진다")
    void resolveGameRoomId_NotNumber_ThrowException() {
        assertThatThrownBy(() -> gameWebSocketPathResolver.resolveGameRoomId(
                URI.create("http://localhost/ws/game/not-number")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolveGameRoomId - 0 이하이면 예외를 던진다")
    void resolveGameRoomId_NotPositive_ThrowException() {
        assertThatThrownBy(() -> gameWebSocketPathResolver.resolveGameRoomId(
                URI.create("http://localhost/ws/game/0")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
