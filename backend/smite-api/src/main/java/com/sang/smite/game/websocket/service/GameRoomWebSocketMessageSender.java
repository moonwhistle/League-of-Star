package com.sang.smite.game.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GameRoomWebSocketMessageSender {

    private final ObjectMapper objectMapper;
    private final GameRoomWebSocketSessionRegistry sessionRegistry;

    public void send(WebSocketSession session, GameWebSocketServerMessage message) throws IOException {
        send(session, objectMapper.writeValueAsString(message));
    }

    public void broadcast(Long gameRoomId, GameWebSocketServerMessage message) throws IOException {
        broadcast(sessionRegistry.findByRoom(gameRoomId), message);
    }

    public void broadcast(List<GameRoomWebSocketSession> sessions, GameWebSocketServerMessage message) throws IOException {
        String payload = objectMapper.writeValueAsString(message);
        for (GameRoomWebSocketSession session : sessions) {
            send(session.getWebSocketSession(), payload);
        }
    }

    private void send(WebSocketSession session, String payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(payload));
        }
    }
}
