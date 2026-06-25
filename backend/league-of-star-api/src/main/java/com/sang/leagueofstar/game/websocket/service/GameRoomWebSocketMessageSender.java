package com.sang.leagueofstar.game.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.leagueofstar.game.websocket.session.GameRoomWebSocketSession;
import com.sang.leagueofstar.game.websocket.session.GameRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

@Slf4j
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
            sendSafely(session.getWebSocketSession(), payload);
        }
    }

    private void sendSafely(WebSocketSession session, String payload) {
        try {
            send(session, payload);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to send game WebSocket message. sessionId={}", session.getId(), e);
        }
    }

    private void send(WebSocketSession session, String payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }
}
