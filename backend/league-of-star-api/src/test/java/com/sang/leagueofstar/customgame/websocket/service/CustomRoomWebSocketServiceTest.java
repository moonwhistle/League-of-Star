package com.sang.leagueofstar.customgame.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.service.CustomGameRoomService;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketMessageType;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomRoomWebSocketServiceTest {

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

    @Test
    @DisplayName("registerSession - session을 등록하고 현재 room snapshot을 ROOM_UPDATED로 전송한다")
    void registerSession_SendRoomUpdatedSnapshot() throws Exception {
        // given
        WebSocketSession session = openSession(SESSION_ID);
        given(customGameRoomService.getWaitingRoom(CUSTOM_ROOM_ID)).willReturn(roomResponse("WAITING"));

        // when
        customRoomWebSocketService.registerSession(CUSTOM_ROOM_ID, USER_ID, session);

        // then
        assertThat(sessionRegistry.findBySessionId(SESSION_ID)).isPresent();
        JsonNode message = sentJson(session);
        assertThat(message.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED.name());
        assertThat(message.get("payload").get("roomId").asLong()).isEqualTo(CUSTOM_ROOM_ID);
        assertThat(message.get("payload").get("status").asText()).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("registerSession - snapshot 조회 실패 시 registry에서 제거하고 연결을 닫는다")
    void registerSession_SnapshotFailed_CloseSession() throws Exception {
        // given
        WebSocketSession session = openSession(SESSION_ID);
        given(customGameRoomService.getWaitingRoom(CUSTOM_ROOM_ID))
                .willThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));

        // when
        customRoomWebSocketService.registerSession(CUSTOM_ROOM_ID, USER_ID, session);

        // then
        assertThat(sessionRegistry.findBySessionId(SESSION_ID)).isEmpty();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("handleUnsupportedClientMessage - ERROR 메시지를 전송하고 연결은 유지한다")
    void handleUnsupportedClientMessage_SendError() throws Exception {
        // given
        WebSocketSession session = openSession(SESSION_ID);

        // when
        customRoomWebSocketService.handleUnsupportedClientMessage(session);

        // then
        JsonNode message = sentJson(session);
        assertThat(message.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ERROR.name());
        assertThat(message.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
    }

    @Test
    @DisplayName("cleanupSession - sessionId 기준으로 registry에서만 제거한다")
    void cleanupSession_UnregisterOnly() {
        // given
        WebSocketSession session = openSession(SESSION_ID);
        given(customGameRoomService.getWaitingRoom(CUSTOM_ROOM_ID)).willReturn(roomResponse("WAITING"));
        customRoomWebSocketService.registerSession(CUSTOM_ROOM_ID, USER_ID, session);

        // when
        customRoomWebSocketService.cleanupSession(session);

        // then
        assertThat(sessionRegistry.findBySessionId(SESSION_ID)).isEmpty();
    }

    private WebSocketSession openSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private JsonNode sentJson(WebSocketSession session) throws Exception {
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
