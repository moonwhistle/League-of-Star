package com.sang.smite.game.websocket.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.game.websocket.dto.GameWebSocketClientMessage;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import com.sang.smite.game.websocket.session.GameWebSocketSessionAttribute;
import com.sang.smite.game.websocket.service.GameRoomWebSocketMessageSender;
import com.sang.smite.game.websocket.service.GameWaitingWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWaitingWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final GameRoomWebSocketSessionRegistry sessionRegistry;
    private final GameWaitingWebSocketService gameWaitingWebSocketService;
    private final GameRoomWebSocketMessageSender messageSender;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long gameRoomId = getRequiredLongAttribute(session, GameWebSocketSessionAttribute.GAME_ROOM_ID);
        Long userId = getRequiredLongAttribute(session, GameWebSocketSessionAttribute.USER_ID);
        if (gameRoomId == null || userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        gameWaitingWebSocketService.registerSession(gameRoomId, userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Optional<GameRoomWebSocketSession> currentSession = sessionRegistry.findBySessionId(session.getId());
        if (currentSession.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        GameWebSocketClientMessage clientMessage;
        try {
            clientMessage = objectMapper.readValue(message.getPayload(), GameWebSocketClientMessage.class);
        } catch (JsonProcessingException e) {
            send(session, GameWebSocketServerMessage.invalidMessageType());
            return;
        }

        if (!isClientMessage(clientMessage)) {
            send(session, GameWebSocketServerMessage.invalidMessageType());
            return;
        }

        if (clientMessage.isClientReady()) {
            gameWaitingWebSocketService.handleClientReady(currentSession.get());
            return;
        }
        if (clientMessage.isRttPong()) {
            gameWaitingWebSocketService.handleRttPong(currentSession.get(), clientMessage);
            return;
        }

        send(session, GameWebSocketServerMessage.invalidMessageType());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gameWaitingWebSocketService.cleanupSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Game waiting WebSocket transport error. sessionId={}", session.getId(), exception);
        gameWaitingWebSocketService.cleanupSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private boolean isClientMessage(GameWebSocketClientMessage clientMessage) {
        return clientMessage.type() != null
                && clientMessage.type().isClientMessage();
    }

    private Long getRequiredLongAttribute(WebSocketSession session, String attributeName) {
        Map<String, Object> attributes = session.getAttributes();
        Object value = attributes.get(attributeName);
        if (value instanceof Long longValue) {
            return longValue;
        }
        log.warn("Missing game WebSocket session attribute. sessionId={}, attribute={}",
                session.getId(), attributeName);
        return null;
    }

    private void send(WebSocketSession session, GameWebSocketServerMessage message) throws IOException {
        messageSender.send(session, message);
    }
}
