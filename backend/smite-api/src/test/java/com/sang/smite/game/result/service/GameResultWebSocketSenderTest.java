package com.sang.smite.game.result.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.game.result.dto.GameResultPayload;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameResultWebSocketSenderTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "session-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRoomWebSocketSessionRegistry sessionRegistry = new GameRoomWebSocketSessionRegistry();
    private final GameRoomWebSocketMessageSender messageSender = new GameRoomWebSocketMessageSender(
            objectMapper,
            sessionRegistry
    );
    private final GameResultWebSocketSender sender = new GameResultWebSocketSender(messageSender);

    @Test
    @DisplayName("sendGameResult - 현재 session에 GAME_RESULT를 전송한다")
    void sendGameResult() throws Exception {
        // given
        WebSocketSession session = session();
        GameResultPayload payload = new GameResultPayload(
                GAME_ROOM_ID,
                GameResult.PLAYER1_WIN,
                USER_ID,
                "SMITE_KILL",
                20_000L,
                List.of()
        );

        // when
        sender.sendGameResult(session, payload);

        // then
        JsonNode message = sentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.GAME_RESULT.name());
        assertThat(message.get("payload").get("gameRoomId").asLong()).isEqualTo(GAME_ROOM_ID);
        assertThat(message.get("payload").get("result").asText()).isEqualTo(GameResult.PLAYER1_WIN.name());
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

    @Test
    @DisplayName("broadcastGameResult - GAME_RESULT payload에는 record/rank summary 정보를 포함하지 않는다")
    void broadcastGameResult_ExcludeRecordRankSummary() throws Exception {
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
        JsonNode payloadNode = sentMessage(session).get("payload");
        assertThat(payloadNode.has("lpBefore")).isFalse();
        assertThat(payloadNode.has("lpAfter")).isFalse();
        assertThat(payloadNode.has("lpChange")).isFalse();
        assertThat(payloadNode.has("rankBefore")).isFalse();
        assertThat(payloadNode.has("rankAfter")).isFalse();
        assertThat(payloadNode.has("seriesType")).isFalse();
        assertThat(payloadNode.has("rankSeriesId")).isFalse();
    }

    @Test
    @DisplayName("broadcastGameResult - 연결된 session이 없으면 메시지 전송 없이 완료한다")
    void broadcastGameResult_NoSession() throws Exception {
        // given
        WebSocketSession session = session();
        GameResultPayload payload = new GameResultPayload(
                GAME_ROOM_ID,
                GameResult.DRAW,
                null,
                "NATURAL_DEATH_DRAW",
                20_000L,
                List.of()
        );

        // when & then
        assertDoesNotThrow(() -> sender.broadcastGameResult(GAME_ROOM_ID, payload));
        verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
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
