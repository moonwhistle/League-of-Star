package com.sang.leagueofstar.customgame.websocket.handler;

import com.sang.leagueofstar.customgame.websocket.service.CustomRoomWebSocketService;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionAttribute;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomRoomWebSocketHandler extends TextWebSocketHandler {

    private final CustomRoomWebSocketSessionRegistry sessionRegistry;
    private final CustomRoomWebSocketService customRoomWebSocketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long customRoomId = getRequiredLongAttribute(session, CustomRoomWebSocketSessionAttribute.CUSTOM_ROOM_ID);
        Long userId = getRequiredLongAttribute(session, CustomRoomWebSocketSessionAttribute.USER_ID);
        if (customRoomId == null || userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        customRoomWebSocketService.registerSession(customRoomId, userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (sessionRegistry.findBySessionId(session.getId()).isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        customRoomWebSocketService.handleUnsupportedClientMessage(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        customRoomWebSocketService.cleanupSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Custom Room WebSocket transport error. sessionId={}", session.getId(), exception);
        customRoomWebSocketService.cleanupSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private Long getRequiredLongAttribute(WebSocketSession session, String attributeName) {
        Map<String, Object> attributes = session.getAttributes();
        Object value = attributes.get(attributeName);
        if (value instanceof Long longValue) {
            return longValue;
        }
        log.warn("Missing custom room WebSocket session attribute. sessionId={}, attribute={}",
                session.getId(), attributeName);
        return null;
    }
}
