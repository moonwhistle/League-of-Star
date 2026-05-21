package com.sang.smite.game.smite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.game.smite.dto.GameResultPayload;
import com.sang.smite.game.smite.dto.SmiteResultPayload;
import com.sang.smite.game.websocket.dto.GameWebSocketMessageType;
import com.sang.smite.game.websocket.service.GameRoomWebSocketMessageSender;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSmiteWebSocketSenderTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "session-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRoomWebSocketSessionRegistry sessionRegistry = new GameRoomWebSocketSessionRegistry();
    private final GameRoomWebSocketMessageSender messageSender = new GameRoomWebSocketMessageSender(
            objectMapper,
            sessionRegistry
    );
    private final GameSmiteWebSocketSender sender = new GameSmiteWebSocketSender(messageSender);

    @Test
    @DisplayName("sendSmiteResult - 현재 session에 SMITE_RESULT를 전송한다")
    void sendSmiteResult() throws Exception {
        // given
        WebSocketSession session = session();
        SmiteResultPayload payload = new SmiteResultPayload(
                GAME_ROOM_ID,
                USER_ID,
                10_000L,
                900,
                1_100,
                1_200,
                0,
                true,
                false
        );

        // when
        sender.sendSmiteResult(session, payload);

        // then
        JsonNode message = sentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.SMITE_RESULT.name());
        assertThat(message.get("payload").get("gameRoomId").asLong()).isEqualTo(GAME_ROOM_ID);
        assertThat(message.get("payload").get("afterHp").asInt()).isZero();
    }

    @Test
    @DisplayName("broadcastGameResult - gameRoom session에 GAME_RESULT를 broadcast한다")
    void broadcastGameResult() throws Exception {
        // given
        WebSocketSession session = session();
        sessionRegistry.register(GAME_ROOM_ID, USER_ID, session);
        GameResultPayload payload = new GameResultPayload(
                GAME_ROOM_ID,
                GameResult.PLAYER1_WIN,
                USER_ID,
                "SMITE_KILL",
                20_000L,
                List.of()
        );

        // when
        sender.broadcastGameResult(GAME_ROOM_ID, payload);

        // then
        JsonNode message = sentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.GAME_RESULT.name());
        assertThat(message.get("payload").get("result").asText()).isEqualTo(GameResult.PLAYER1_WIN.name());
        assertThat(message.get("payload").get("reason").asText()).isEqualTo("SMITE_KILL");
    }

    private WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private JsonNode sentMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        return objectMapper.readTree(messageCaptor.getValue().getPayload());
    }
}
