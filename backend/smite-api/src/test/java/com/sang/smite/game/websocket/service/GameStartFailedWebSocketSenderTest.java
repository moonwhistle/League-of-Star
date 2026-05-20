package com.sang.smite.game.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.game.websocket.dto.GameWebSocketMessageType;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStartFailedWebSocketSenderTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRoomWebSocketSessionRegistry sessionRegistry = new GameRoomWebSocketSessionRegistry();
    private final GameStartFailedWebSocketSender sender = new GameStartFailedWebSocketSender(
            objectMapper,
            sessionRegistry
    );

    @Test
    @DisplayName("연결된 gameRoom session에 GAME_START_FAILED를 전송하고 close 후 registry에서 제거한다")
    void sendFailure() throws Exception {
        // given
        WebSocketSession firstSession = session("session-1");
        WebSocketSession secondSession = session("session-2");
        sessionRegistry.register(GAME_ROOM_ID, FIRST_USER_ID, firstSession);
        sessionRegistry.register(GAME_ROOM_ID, SECOND_USER_ID, secondSession);

        // when
        sender.sendFailure(GAME_ROOM_ID, "RTT_FAILED", "GO_TO_MATCH_START");

        // then
        assertFailureMessage(firstSession);
        assertFailureMessage(secondSession);
        verify(firstSession).close(CloseStatus.NORMAL);
        verify(secondSession).close(CloseStatus.NORMAL);
        assertThat(sessionRegistry.findByRoom(GAME_ROOM_ID)).isEmpty();
    }

    @Test
    @DisplayName("현재 인스턴스에 gameRoom session이 없으면 no-op 처리한다")
    void sendFailure_NoLocalSession() {
        assertThatCode(() -> sender.sendFailure(GAME_ROOM_ID, "RTT_FAILED", "GO_TO_MATCH_START"))
                .doesNotThrowAnyException();
    }

    private WebSocketSession session(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private void assertFailureMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        JsonNode json = objectMapper.readTree(messageCaptor.getValue().getPayload());
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.GAME_START_FAILED.name());
        assertThat(json.get("payload").get("gameRoomId").asLong()).isEqualTo(GAME_ROOM_ID);
        assertThat(json.get("payload").get("reason").asText()).isEqualTo("RTT_FAILED");
        assertThat(json.get("payload").get("action").asText()).isEqualTo("GO_TO_MATCH_START");
    }
}
