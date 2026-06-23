package com.sang.leagueofstar.customgame.websocket.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomRoomWebSocketPathResolverTest {

    private final CustomRoomWebSocketPathResolver customRoomWebSocketPathResolver =
            new CustomRoomWebSocketPathResolver();

    @Test
    @DisplayName("resolveRoomId - WebSocket path 마지막 segment를 customRoomId로 변환한다")
    void resolveRoomId_Success() {
        // when
        Long result = customRoomWebSocketPathResolver.resolveRoomId(
                URI.create("http://localhost/ws/custom-games/rooms/100?token=access-token")
        );

        // then
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("resolveRoomId - 마지막 path segment가 없으면 예외를 던진다")
    void resolveRoomId_MissingPathSegment_ThrowException() {
        assertThatThrownBy(() -> customRoomWebSocketPathResolver.resolveRoomId(
                URI.create("http://localhost/ws/custom-games/rooms/")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolveRoomId - 숫자가 아니면 예외를 던진다")
    void resolveRoomId_NotNumber_ThrowException() {
        assertThatThrownBy(() -> customRoomWebSocketPathResolver.resolveRoomId(
                URI.create("http://localhost/ws/custom-games/rooms/not-number")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolveRoomId - 0 이하이면 예외를 던진다")
    void resolveRoomId_NotPositive_ThrowException() {
        assertThatThrownBy(() -> customRoomWebSocketPathResolver.resolveRoomId(
                URI.create("http://localhost/ws/custom-games/rooms/0")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
