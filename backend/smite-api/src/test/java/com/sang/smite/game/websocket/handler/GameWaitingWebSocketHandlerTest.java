package com.sang.smite.game.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.game.websocket.dto.GameWebSocketMessageType;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import com.sang.smite.game.websocket.session.GameWebSocketSessionAttribute;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.service.GameWaitingReadyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameWaitingWebSocketHandlerTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final String FIRST_SESSION_ID = "session-1";
    private static final String SECOND_SESSION_ID = "session-2";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRoomWebSocketSessionRegistry sessionRegistry = new GameRoomWebSocketSessionRegistry();
    private final GameWaitingReadyService gameWaitingReadyService = mock(GameWaitingReadyService.class);
    private final GameWaitingWebSocketHandler handler = new GameWaitingWebSocketHandler(
            objectMapper,
            sessionRegistry,
            gameWaitingReadyService
    );

    @Test
    @DisplayName("afterConnectionEstablished - session attributes 기준으로 registry 등록 후 PLAYER_JOINED를 전송한다")
    void afterConnectionEstablished_RegisterAndBroadcastJoined() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);

        // when
        handler.afterConnectionEstablished(session);

        // then
        assertThat(sessionRegistry.isConnected(GAME_ROOM_ID, FIRST_USER_ID)).isTrue();
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_JOINED.name());
        assertThat(message.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
    }

    @Test
    @DisplayName("afterConnectionEstablished - session attributes가 없으면 연결을 종료한다")
    void afterConnectionEstablished_MissingAttributes_Close() throws Exception {
        // given
        WebSocketSession session = sessionWithoutAttributes(FIRST_SESSION_ID);

        // when
        handler.afterConnectionEstablished(session);

        // then
        assertThat(sessionRegistry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("handleTextMessage - CLIENT_READY 수신 시 READY 상태를 저장하고 PLAYER_READY를 broadcast한다")
    void handleTextMessage_ClientReady() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID, FIRST_USER_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID, SECOND_USER_ID);
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);
        clearInvocations(firstSession, secondSession);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(false));

        // when
        handler.handleTextMessage(firstSession, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));

        // then
        assertThat(sessionRegistry.areBothReady(GAME_ROOM_ID)).isFalse();
        JsonNode firstMessage = lastSentMessage(firstSession);
        JsonNode secondMessage = lastSentMessage(secondSession);
        assertThat(firstMessage.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_READY.name());
        assertThat(secondMessage.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_READY.name());
        assertThat(firstMessage.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
        assertThat(firstMessage.get("payload").get("bothReady").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("handleTextMessage - 양쪽 CLIENT_READY 수신 시 bothReady=true로 broadcast한다")
    void handleTextMessage_BothReady() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID, FIRST_USER_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID, SECOND_USER_ID);
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(false));
        handler.handleTextMessage(firstSession, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));
        clearInvocations(firstSession, secondSession);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, SECOND_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(true));

        // when
        handler.handleTextMessage(secondSession, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));

        // then
        assertThat(sessionRegistry.areBothReady(GAME_ROOM_ID)).isTrue();
        JsonNode message = lastSentMessage(firstSession);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_READY.name());
        assertThat(message.get("payload").get("userId").asLong()).isEqualTo(SECOND_USER_ID);
        assertThat(message.get("payload").get("bothReady").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("handleTextMessage - server-only type을 클라이언트가 보내면 ERROR를 응답한다")
    void handleTextMessage_ServerOnlyType_ReturnError() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"PLAYER_READY\",\"payload\":{}}"));

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.ERROR.name());
        assertThat(message.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
        assertThat(sessionRegistry.areBothReady(GAME_ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("handleTextMessage - payload의 userId/gameRoomId를 신뢰하지 않고 session attributes를 사용한다")
    void handleTextMessage_IgnorePayloadIdentity() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(false));

        // when
        handler.handleTextMessage(
                session,
                new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{\"gameRoomId\":999,\"userId\":999}}")
        );

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_READY.name());
        assertThat(message.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
        assertThat(sessionRegistry.findByRoomAndUser(GAME_ROOM_ID, FIRST_USER_ID))
                .get()
                .matches(GameRoomWebSocketSession::isReady);
        assertThat(sessionRegistry.findByRoomAndUser(999L, 999L)).isEmpty();
    }

    @Test
    @DisplayName("handleTextMessage - Redis waiting 상태가 없으면 READY 처리 없이 연결을 종료한다")
    void handleTextMessage_WaitingStateRejected_Close() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.rejected());

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));

        // then
        assertThat(sessionRegistry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("handleTextMessage - JSON 파싱 실패 시 ERROR를 응답한다")
    void handleTextMessage_InvalidJson_ReturnError() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        // when
        handler.handleTextMessage(session, new TextMessage("{invalid-json"));

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.ERROR.name());
        assertThat(message.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
    }

    @Test
    @DisplayName("afterConnectionClosed - registry에서 제거하고 남은 참가자에게 PLAYER_LEFT를 전송한다")
    void afterConnectionClosed_UnregisterAndBroadcastLeft() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID, FIRST_USER_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID, SECOND_USER_ID);
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);
        clearInvocations(firstSession, secondSession);

        // when
        handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);

        // then
        assertThat(sessionRegistry.isConnected(GAME_ROOM_ID, FIRST_USER_ID)).isFalse();
        assertThat(sessionRegistry.isConnected(GAME_ROOM_ID, SECOND_USER_ID)).isTrue();
        JsonNode message = lastSentMessage(secondSession);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_LEFT.name());
        assertThat(message.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
    }

    private WebSocketSession session(String sessionId, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes(userId));
        return session;
    }

    private WebSocketSession sessionWithoutAttributes(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        return session;
    }

    private Map<String, Object> attributes(Long userId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GameWebSocketSessionAttribute.GAME_ROOM_ID, GAME_ROOM_ID);
        attributes.put(GameWebSocketSessionAttribute.USER_ID, userId);
        return attributes;
    }

    private JsonNode lastSentMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(messageCaptor.capture());
        TextMessage lastMessage = messageCaptor.getAllValues().get(messageCaptor.getAllValues().size() - 1);
        return objectMapper.readTree(lastMessage.getPayload());
    }
}
