package com.sang.leagueofstar.customgame.websocket.session;

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

class CustomRoomWebSocketSessionRegistryTest {

    private static final Long CUSTOM_ROOM_ID = 100L;
    private static final Long OTHER_CUSTOM_ROOM_ID = 200L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final String FIRST_SESSION_ID = "session-1";
    private static final String SECOND_SESSION_ID = "session-2";
    private static final String THIRD_SESSION_ID = "session-3";
    private static final String RECONNECTED_SESSION_ID = "session-4";

    private final CustomRoomWebSocketSessionRegistry registry = new CustomRoomWebSocketSessionRegistry();

    @Test
    @DisplayName("register - customRoomId/userId/sessionId 기준으로 연결을 저장한다")
    void register_Success() {
        // given
        WebSocketSession webSocketSession = session(FIRST_SESSION_ID);

        // when
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, webSocketSession);

        // then
        Optional<CustomRoomWebSocketSession> foundBySessionId = registry.findBySessionId(FIRST_SESSION_ID);
        Optional<CustomRoomWebSocketSession> foundByRoomAndUser = registry.findByRoomAndUser(
                CUSTOM_ROOM_ID,
                FIRST_USER_ID
        );
        assertThat(foundBySessionId).isPresent();
        assertThat(foundByRoomAndUser).isPresent();
        assertThat(foundBySessionId.get()).isSameAs(foundByRoomAndUser.get());
    }

    @Test
    @DisplayName("unregister - sessionId 기준으로 연결을 제거한다")
    void unregister_Success() {
        // given
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, session(FIRST_SESSION_ID));

        // when
        registry.unregister(FIRST_SESSION_ID);

        // then
        assertThat(registry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        assertThat(registry.findByRoomAndUser(CUSTOM_ROOM_ID, FIRST_USER_ID)).isEmpty();
        assertThat(registry.findByRoom(CUSTOM_ROOM_ID)).isEmpty();
    }

    @Test
    @DisplayName("register - 같은 room/user가 다시 연결하면 기존 session을 닫고 새 session으로 교체한다")
    void register_DuplicateRoomUser_ReplaceSession() throws Exception {
        // given
        WebSocketSession previousSession = session(FIRST_SESSION_ID);
        WebSocketSession newSession = session(RECONNECTED_SESSION_ID);
        when(previousSession.isOpen()).thenReturn(true);
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, previousSession);

        // when
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, newSession);

        // then
        assertThat(registry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        assertThat(registry.findBySessionId(RECONNECTED_SESSION_ID)).isPresent();
        assertThat(registry.findByRoomAndUser(CUSTOM_ROOM_ID, FIRST_USER_ID))
                .get()
                .extracting(CustomRoomWebSocketSession::getSessionId)
                .isEqualTo(RECONNECTED_SESSION_ID);
        verify(previousSession).close(CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("findByRoom - 같은 room에 연결된 session 목록의 복사본을 반환한다")
    void findByRoom_ReturnCopy() {
        // given
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, session(FIRST_SESSION_ID));
        registry.register(CUSTOM_ROOM_ID, SECOND_USER_ID, session(SECOND_SESSION_ID));

        // when
        List<CustomRoomWebSocketSession> sessions = registry.findByRoom(CUSTOM_ROOM_ID);
        sessions.clear();

        // then
        assertThat(registry.findByRoom(CUSTOM_ROOM_ID))
                .extracting(CustomRoomWebSocketSession::getUserId)
                .containsExactlyInAnyOrder(FIRST_USER_ID, SECOND_USER_ID);
    }

    @Test
    @DisplayName("register - 같은 WebSocket sessionId가 다른 room/user로 등록되면 기존 등록을 제거한다")
    void register_DuplicateSessionId_RemovePreviousIndex() {
        // given
        WebSocketSession webSocketSession = session(FIRST_SESSION_ID);
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, webSocketSession);

        // when
        registry.register(OTHER_CUSTOM_ROOM_ID, SECOND_USER_ID, webSocketSession);

        // then
        assertThat(registry.findByRoomAndUser(CUSTOM_ROOM_ID, FIRST_USER_ID)).isEmpty();
        assertThat(registry.findByRoomAndUser(OTHER_CUSTOM_ROOM_ID, SECOND_USER_ID)).isPresent();
        assertThat(registry.findBySessionId(FIRST_SESSION_ID))
                .get()
                .extracting(CustomRoomWebSocketSession::getCustomRoomId)
                .isEqualTo(OTHER_CUSTOM_ROOM_ID);
    }

    @Test
    @DisplayName("closeAndUnregister - room/user 기준으로 session을 닫고 제거한다")
    void closeAndUnregister_Success() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID);
        when(firstSession.isOpen()).thenReturn(true);
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, firstSession);
        registry.register(CUSTOM_ROOM_ID, SECOND_USER_ID, secondSession);

        // when
        registry.closeAndUnregister(CUSTOM_ROOM_ID, FIRST_USER_ID);

        // then
        assertThat(registry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        assertThat(registry.findByRoomAndUser(CUSTOM_ROOM_ID, FIRST_USER_ID)).isEmpty();
        assertThat(registry.findByRoomAndUser(CUSTOM_ROOM_ID, SECOND_USER_ID)).isPresent();
        verify(firstSession).close(CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("closeAndUnregisterRoom - room 기준으로 모든 session을 닫고 제거한다")
    void closeAndUnregisterRoom_Success() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID);
        WebSocketSession thirdSession = session(THIRD_SESSION_ID);
        when(firstSession.isOpen()).thenReturn(true);
        when(secondSession.isOpen()).thenReturn(true);
        registry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, firstSession);
        registry.register(CUSTOM_ROOM_ID, SECOND_USER_ID, secondSession);
        registry.register(OTHER_CUSTOM_ROOM_ID, FIRST_USER_ID, thirdSession);

        // when
        registry.closeAndUnregisterRoom(CUSTOM_ROOM_ID);

        // then
        assertThat(registry.findByRoom(CUSTOM_ROOM_ID)).isEmpty();
        assertThat(registry.findByRoomAndUser(OTHER_CUSTOM_ROOM_ID, FIRST_USER_ID)).isPresent();
        verify(firstSession).close(CloseStatus.NORMAL);
        verify(secondSession).close(CloseStatus.NORMAL);
    }

    private WebSocketSession session(String sessionId) {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);
        when(webSocketSession.getId()).thenReturn(sessionId);
        return webSocketSession;
    }
}
