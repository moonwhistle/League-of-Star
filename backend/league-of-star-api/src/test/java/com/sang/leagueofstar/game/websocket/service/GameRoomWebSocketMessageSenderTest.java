package com.sang.leagueofstar.game.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.game.websocket.dto.GameWebSocketMessageType;
import com.sang.leagueofstar.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.leagueofstar.game.websocket.session.GameRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameRoomWebSocketMessageSenderTest {

    private static final Long GAME_ROOM_ID = 200L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRoomWebSocketSessionRegistry sessionRegistry = new GameRoomWebSocketSessionRegistry();
    private final GameRoomWebSocketMessageSender sender = new GameRoomWebSocketMessageSender(
            objectMapper,
            sessionRegistry
    );

    @Test
    @DisplayName("send - open session에 Game WebSocket 메시지를 전송한다")
    void send_OpenSession() throws Exception {
        // given
        WebSocketSession session = openSession("session-1");

        // when
        sender.send(session, GameWebSocketServerMessage.playerJoined(FIRST_USER_ID));

        // then
        JsonNode json = sentJson(session);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_JOINED.name());
        assertThat(json.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
    }

    @Test
    @DisplayName("send - closed session이면 메시지를 보내지 않는다")
    void send_ClosedSession_NoOp() throws Exception {
        // given
        WebSocketSession session = session("session-1", false);

        // when
        sender.send(session, GameWebSocketServerMessage.playerJoined(FIRST_USER_ID));

        // then
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("broadcast - 한 session 전송이 실패해도 나머지 session에는 계속 전송한다")
    void broadcast_SendFailed_ContinuesRemainingSessions() throws Exception {
        // given
        WebSocketSession failedSession = openSession("session-1");
        WebSocketSession remainingSession = openSession("session-2");
        doThrow(new IOException("send failed"))
                .when(failedSession)
                .sendMessage(any(TextMessage.class));
        sessionRegistry.register(GAME_ROOM_ID, FIRST_USER_ID, failedSession);
        sessionRegistry.register(GAME_ROOM_ID, SECOND_USER_ID, remainingSession);

        // when & then
        assertThatCode(() -> sender.broadcast(
                GAME_ROOM_ID,
                GameWebSocketServerMessage.playerJoined(FIRST_USER_ID)
        )).doesNotThrowAnyException();
        JsonNode json = sentJson(remainingSession);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_JOINED.name());
        assertThat(json.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
    }

    @Test
    @DisplayName("broadcast - 현재 인스턴스에 game room session이 없으면 no-op 처리한다")
    void broadcast_NoLocalSession() {
        assertThatCode(() -> sender.broadcast(
                GAME_ROOM_ID,
                GameWebSocketServerMessage.playerJoined(FIRST_USER_ID)
        )).doesNotThrowAnyException();
    }

    private WebSocketSession openSession(String sessionId) {
        return session(sessionId, true);
    }

    private WebSocketSession session(String sessionId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    private JsonNode sentJson(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        return objectMapper.readTree(messageCaptor.getValue().getPayload());
    }
}
