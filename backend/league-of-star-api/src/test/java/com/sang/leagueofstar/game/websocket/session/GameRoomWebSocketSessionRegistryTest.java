package com.sang.leagueofstar.game.websocket.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameRoomWebSocketSessionRegistryTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final String FIRST_SESSION_ID = "session-1";
    private static final String SECOND_SESSION_ID = "session-2";
    private static final String RECONNECTED_SESSION_ID = "session-3";

    private final GameRoomWebSocketSessionRegistry registry = new GameRoomWebSocketSessionRegistry();

    @Test
    @DisplayName("register - gameRoomId/userId/sessionId 기준으로 연결을 저장한다")
    void register_Success() {
        // given
        WebSocketSession webSocketSession = session(FIRST_SESSION_ID);

        // when
        registry.register(GAME_ROOM_ID, FIRST_USER_ID, webSocketSession);

        // then
        Optional<GameRoomWebSocketSession> foundBySessionId = registry.findBySessionId(FIRST_SESSION_ID);
        Optional<GameRoomWebSocketSession> foundByRoomAndUser = registry.findByRoomAndUser(
                GAME_ROOM_ID,
                FIRST_USER_ID
        );
        assertThat(foundBySessionId).isPresent();
        assertThat(foundByRoomAndUser).isPresent();
        assertThat(foundBySessionId.get()).isSameAs(foundByRoomAndUser.get());
        assertThat(registry.isConnected(GAME_ROOM_ID, FIRST_USER_ID)).isTrue();
    }

    @Test
    @DisplayName("unregister - sessionId 기준으로 연결을 제거한다")
    void unregister_Success() {
        // given
        WebSocketSession webSocketSession = session(FIRST_SESSION_ID);
        registry.register(GAME_ROOM_ID, FIRST_USER_ID, webSocketSession);

        // when
        registry.unregister(FIRST_SESSION_ID);

        // then
        assertThat(registry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        assertThat(registry.findByRoomAndUser(GAME_ROOM_ID, FIRST_USER_ID)).isEmpty();
        assertThat(registry.isConnected(GAME_ROOM_ID, FIRST_USER_ID)).isFalse();
    }

    @Test
    @DisplayName("register - 같은 유저가 다시 연결하면 기존 session을 닫고 새 session으로 교체한다")
    void register_DuplicateUser_ReplaceSession() throws Exception {
        // given
        WebSocketSession previousSession = session(FIRST_SESSION_ID);
        WebSocketSession newSession = session(RECONNECTED_SESSION_ID);
        when(previousSession.isOpen()).thenReturn(true);
        registry.register(GAME_ROOM_ID, FIRST_USER_ID, previousSession);

        // when
        registry.register(GAME_ROOM_ID, FIRST_USER_ID, newSession);

        // then
        assertThat(registry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        assertThat(registry.findBySessionId(RECONNECTED_SESSION_ID)).isPresent();
        assertThat(registry.findByRoomAndUser(GAME_ROOM_ID, FIRST_USER_ID))
                .get()
                .extracting(GameRoomWebSocketSession::getSessionId)
                .isEqualTo(RECONNECTED_SESSION_ID);
        verify(previousSession).close(CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("areBothConnected - gameRoom에 두 참가자가 연결되면 true를 반환한다")
    void areBothConnected() {
        // given
        registry.register(GAME_ROOM_ID, FIRST_USER_ID, session(FIRST_SESSION_ID));
        registry.register(GAME_ROOM_ID, SECOND_USER_ID, session(SECOND_SESSION_ID));

        // when & then
        assertThat(registry.areBothConnected(GAME_ROOM_ID)).isTrue();
        assertThat(registry.findByRoom(GAME_ROOM_ID))
                .extracting(GameRoomWebSocketSession::getUserId)
                .containsExactlyInAnyOrder(FIRST_USER_ID, SECOND_USER_ID);
    }

    @Test
    @DisplayName("markReady - 연결된 유저의 READY 상태를 저장하고 양쪽 READY 여부를 계산한다")
    void markReady() {
        // given
        registry.register(GAME_ROOM_ID, FIRST_USER_ID, session(FIRST_SESSION_ID));
        registry.register(GAME_ROOM_ID, SECOND_USER_ID, session(SECOND_SESSION_ID));

        // when & then
        assertThat(registry.markReady(GAME_ROOM_ID, FIRST_USER_ID)).isTrue();
        assertThat(registry.areBothReady(GAME_ROOM_ID)).isFalse();

        assertThat(registry.markReady(GAME_ROOM_ID, SECOND_USER_ID)).isTrue();
        assertThat(registry.areBothReady(GAME_ROOM_ID)).isTrue();
    }

    @Test
    @DisplayName("findByRoom - 연결된 session 목록의 복사본을 반환한다")
    void findByRoom_ReturnCopy() {
        // given
        registry.register(GAME_ROOM_ID, FIRST_USER_ID, session(FIRST_SESSION_ID));

        // when
        List<GameRoomWebSocketSession> sessions = registry.findByRoom(GAME_ROOM_ID);
        sessions.clear();

        // then
        assertThat(registry.findByRoom(GAME_ROOM_ID)).hasSize(1);
    }

    @Test
    @DisplayName("markReady - 연결되지 않은 유저이면 false를 반환한다")
    void markReady_NotConnected_ReturnFalse() {
        assertThat(registry.markReady(GAME_ROOM_ID, FIRST_USER_ID)).isFalse();
    }

    private WebSocketSession session(String sessionId) {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);
        when(webSocketSession.getId()).thenReturn(sessionId);
        return webSocketSession;
    }
}
