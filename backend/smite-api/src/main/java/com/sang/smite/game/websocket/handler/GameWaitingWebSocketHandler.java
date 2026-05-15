package com.sang.smite.game.websocket.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.game.websocket.dto.GameWebSocketClientMessage;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import com.sang.smite.game.websocket.session.GameWebSocketSessionAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWaitingWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final GameRoomWebSocketSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long gameRoomId = getRequiredLongAttribute(session, GameWebSocketSessionAttribute.GAME_ROOM_ID);
        Long userId = getRequiredLongAttribute(session, GameWebSocketSessionAttribute.USER_ID);
        if (gameRoomId == null || userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionRegistry.register(gameRoomId, userId, session);
        broadcast(gameRoomId, GameWebSocketServerMessage.playerJoined(userId));
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

        if (!isSupportedClientMessage(clientMessage)) {
            send(session, GameWebSocketServerMessage.invalidMessageType());
            return;
        }

        handleClientReady(currentSession.get());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanupSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Game waiting WebSocket transport error. sessionId={}", session.getId(), exception);
        cleanupSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private boolean isSupportedClientMessage(GameWebSocketClientMessage clientMessage) {
        return clientMessage.type() != null
                && clientMessage.type().isClientMessage()
                && clientMessage.isClientReady();
    }

    private void handleClientReady(GameRoomWebSocketSession currentSession) throws IOException {
        Long gameRoomId = currentSession.getGameRoomId();
        Long userId = currentSession.getUserId();
        sessionRegistry.markReady(gameRoomId, userId);

        boolean bothReady = sessionRegistry.areBothReady(gameRoomId);
        broadcast(gameRoomId, GameWebSocketServerMessage.playerReady(userId, bothReady));
    }

    private void cleanupSession(WebSocketSession session) {
        Optional<GameRoomWebSocketSession> currentSession = sessionRegistry.findBySessionId(session.getId());
        if (currentSession.isEmpty()) {
            return;
        }

        GameRoomWebSocketSession removedSession = currentSession.get();
        sessionRegistry.unregister(session.getId());
        try {
            broadcast(
                    removedSession.getGameRoomId(),
                    GameWebSocketServerMessage.playerLeft(removedSession.getUserId())
            );
        } catch (IOException e) {
            log.warn("Failed to broadcast player left message. sessionId={}", session.getId(), e);
        }
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

    private void broadcast(Long gameRoomId, GameWebSocketServerMessage message) throws IOException {
        List<GameRoomWebSocketSession> sessions = sessionRegistry.findByRoom(gameRoomId);
        String payload = objectMapper.writeValueAsString(message);
        for (GameRoomWebSocketSession session : sessions) {
            send(session.getWebSocketSession(), payload);
        }
    }

    private void send(WebSocketSession session, GameWebSocketServerMessage message) throws IOException {
        send(session, objectMapper.writeValueAsString(message));
    }

    private void send(WebSocketSession session, String payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(payload));
        }
    }
}
