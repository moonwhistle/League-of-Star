package com.sang.leagueofstar.customgame.websocket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketServerMessage;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSession;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
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
public class CustomRoomWebSocketMessageSender {

    private final ObjectMapper objectMapper;
    private final CustomRoomWebSocketSessionRegistry sessionRegistry;

    public void send(WebSocketSession session, CustomRoomWebSocketServerMessage message) {
        try {
            send(session, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize custom room WebSocket message. sessionId={}", session.getId(), e);
        }
    }

    public void broadcast(Long customRoomId, CustomRoomWebSocketServerMessage message) {
        broadcast(sessionRegistry.findByRoom(customRoomId), message);
    }

    public void broadcast(
            List<CustomRoomWebSocketSession> sessions,
            CustomRoomWebSocketServerMessage message
    ) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize custom room WebSocket broadcast message.", e);
            return;
        }

        for (CustomRoomWebSocketSession session : sessions) {
            send(session.getWebSocketSession(), payload);
        }
    }

    private void send(WebSocketSession session, String payload) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }

            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.warn("Failed to send custom room WebSocket message. sessionId={}", session.getId(), e);
            }
        }
    }
}
