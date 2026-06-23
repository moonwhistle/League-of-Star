package com.sang.leagueofstar.customgame.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.service.CustomGameRoomService;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketMessageType;
import com.sang.leagueofstar.customgame.websocket.service.CustomRoomWebSocketMessageSender;
import com.sang.leagueofstar.customgame.websocket.service.CustomRoomWebSocketService;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionAttribute;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomRoomWebSocketHandlerTest {

    private static final Long CUSTOM_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "session-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomRoomWebSocketSessionRegistry sessionRegistry = new CustomRoomWebSocketSessionRegistry();
    private final CustomGameRoomService customGameRoomService = mock(CustomGameRoomService.class);
    private final CustomRoomWebSocketMessageSender messageSender = new CustomRoomWebSocketMessageSender(
            objectMapper,
            sessionRegistry
    );
    private final CustomRoomWebSocketService customRoomWebSocketService = new CustomRoomWebSocketService(
            sessionRegistry,
            customGameRoomService,
            messageSender
    );
    private final CustomRoomWebSocketHandler handler = new CustomRoomWebSocketHandler(
            sessionRegistry,
            customRoomWebSocketService
    );

    @Test
    @DisplayName("afterConnectionEstablished - session attributes 기준으로 registry 등록 후 ROOM_UPDATED를 전송한다")
    void afterConnectionEstablished_RegisterAndSendSnapshot() throws Exception {
        // given
        WebSocketSession session = session(SESSION_ID);
        given(customGameRoomService.getWaitingRoom(CUSTOM_ROOM_ID)).willReturn(roomResponse("WAITING"));

        // when
        handler.afterConnectionEstablished(session);

        // then
        assertThat(sessionRegistry.findByRoomAndUser(CUSTOM_ROOM_ID, USER_ID)).isPresent();
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED.name());
        assertThat(message.get("payload").get("roomId").asLong()).isEqualTo(CUSTOM_ROOM_ID);
    }

    @Test
    @DisplayName("afterConnectionEstablished - session attributes가 없으면 연결을 종료한다")
    void afterConnectionEstablished_MissingAttributes_Close() throws Exception {
        // given
        WebSocketSession session = sessionWithoutAttributes(SESSION_ID);

        // when
        handler.afterConnectionEstablished(session);

        // then
        assertThat(sessionRegistry.findBySessionId(SESSION_ID)).isEmpty();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("handleTextMessage - client text message 수신 시 ERROR를 전송하고 연결을 유지한다")
    void handleTextMessage_SendError() throws Exception {
        // given
        WebSocketSession session = session(SESSION_ID);
        given(customGameRoomService.getWaitingRoom(CUSTOM_ROOM_ID)).willReturn(roomResponse("WAITING"));
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"PING\"}"));

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ERROR.name());
        assertThat(sessionRegistry.findBySessionId(SESSION_ID)).isPresent();
    }

    @Test
    @DisplayName("handleTextMessage - 등록되지 않은 session이면 연결을 종료한다")
    void handleTextMessage_UnregisteredSession_Close() throws Exception {
        // given
        WebSocketSession session = session(SESSION_ID);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"PING\"}"));

        // then
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("afterConnectionClosed - registry에서만 session을 제거한다")
    void afterConnectionClosed_Unregister() throws Exception {
        // given
        WebSocketSession session = session(SESSION_ID);
        given(customGameRoomService.getWaitingRoom(CUSTOM_ROOM_ID)).willReturn(roomResponse("WAITING"));
        handler.afterConnectionEstablished(session);
        clearInvocations(customGameRoomService);

        // when
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // then
        assertThat(sessionRegistry.findBySessionId(SESSION_ID)).isEmpty();
        verifyNoInteractions(customGameRoomService);
    }

    @Test
    @DisplayName("handleTransportError - registry만 정리하고 open session을 SERVER_ERROR로 닫는다")
    void handleTransportError_UnregisterAndClose() throws Exception {
        // given
        WebSocketSession session = session(SESSION_ID);
        given(customGameRoomService.getWaitingRoom(CUSTOM_ROOM_ID)).willReturn(roomResponse("WAITING"));
        handler.afterConnectionEstablished(session);
        clearInvocations(customGameRoomService);

        // when
        handler.handleTransportError(session, new RuntimeException("transport error"));

        // then
        assertThat(sessionRegistry.findBySessionId(SESSION_ID)).isEmpty();
        verify(session).close(CloseStatus.SERVER_ERROR);
        verifyNoInteractions(customGameRoomService);
    }

    private WebSocketSession session(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes());
        return session;
    }

    private WebSocketSession sessionWithoutAttributes(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAttributes()).thenReturn(Map.of());
        return session;
    }

    private Map<String, Object> attributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(CustomRoomWebSocketSessionAttribute.CUSTOM_ROOM_ID, CUSTOM_ROOM_ID);
        attributes.put(CustomRoomWebSocketSessionAttribute.USER_ID, USER_ID);
        return attributes;
    }

    private JsonNode lastSentMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        return objectMapper.readTree(messageCaptor.getValue().getPayload());
    }

    private CustomRoomResponse roomResponse(String status) {
        return new CustomRoomResponse(
                CUSTOM_ROOM_ID,
                "Host's room",
                "AB12CD",
                USER_ID,
                status,
                2,
                List.of()
        );
    }
}
