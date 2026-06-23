package com.sang.leagueofstar.customgame.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketMessageType;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketServerMessage;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomRoomWebSocketMessageSenderTest {

    private static final Long CUSTOM_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomRoomWebSocketSessionRegistry sessionRegistry = new CustomRoomWebSocketSessionRegistry();
    private final CustomRoomWebSocketMessageSender sender = new CustomRoomWebSocketMessageSender(
            objectMapper,
            sessionRegistry
    );

    @Test
    @DisplayName("send - open session에 Custom Room WebSocket 메시지를 전송한다")
    void send_OpenSession() throws Exception {
        // given
        WebSocketSession session = openSession("session-1");

        // when
        sender.send(session, CustomRoomWebSocketServerMessage.roomUpdated(roomResponse("WAITING")));

        // then
        JsonNode json = sentJson(session);
        assertThat(json.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED.name());
        assertThat(json.get("payload").get("roomId").asLong()).isEqualTo(CUSTOM_ROOM_ID);
    }

    @Test
    @DisplayName("send - closed session이면 메시지를 보내지 않는다")
    void send_ClosedSession_NoOp() throws Exception {
        // given
        WebSocketSession session = session("session-1", false);

        // when
        sender.send(session, CustomRoomWebSocketServerMessage.roomUpdated(roomResponse("WAITING")));

        // then
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("send - 전송 실패 시 예외를 밖으로 던지지 않는다")
    void send_SendFailed_NoThrow() throws Exception {
        // given
        WebSocketSession session = openSession("session-1");
        org.mockito.Mockito.doThrow(new IOException("send failed"))
                .when(session)
                .sendMessage(any(TextMessage.class));

        // when & then
        assertThatCode(() -> sender.send(
                session,
                CustomRoomWebSocketServerMessage.roomUpdated(roomResponse("WAITING"))
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("broadcast - room에 연결된 open session에 같은 메시지를 전송한다")
    void broadcast_ByRoom() throws Exception {
        // given
        WebSocketSession firstSession = openSession("session-1");
        WebSocketSession secondSession = openSession("session-2");
        sessionRegistry.register(CUSTOM_ROOM_ID, FIRST_USER_ID, firstSession);
        sessionRegistry.register(CUSTOM_ROOM_ID, SECOND_USER_ID, secondSession);

        // when
        sender.broadcast(CUSTOM_ROOM_ID, CustomRoomWebSocketServerMessage.roomUpdated(roomResponse("WAITING")));

        // then
        assertThat(sentJson(firstSession).get("type").asText())
                .isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED.name());
        assertThat(sentJson(secondSession).get("type").asText())
                .isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED.name());
    }

    @Test
    @DisplayName("broadcast - 현재 인스턴스에 room session이 없으면 no-op 처리한다")
    void broadcast_NoLocalSession() {
        assertThatCode(() -> sender.broadcast(
                CUSTOM_ROOM_ID,
                CustomRoomWebSocketServerMessage.roomUpdated(roomResponse("WAITING"))
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

    private CustomRoomResponse roomResponse(String status) {
        return new CustomRoomResponse(
                CUSTOM_ROOM_ID,
                "Host's room",
                "AB12CD",
                FIRST_USER_ID,
                status,
                2,
                List.of()
        );
    }
}
