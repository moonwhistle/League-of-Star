package com.sang.smite.game.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

/**
 * game waiting timeout을 현재 인스턴스의 WebSocket session에 전송합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWaitingTimeoutWebSocketSender {

    private final ObjectMapper objectMapper;
    private final GameRoomWebSocketSessionRegistry sessionRegistry;

    public void sendTimeout(Long gameRoomId, String reason, String action) {
        List<GameRoomWebSocketSession> sessions = sessionRegistry.findByRoom(gameRoomId);
        for (GameRoomWebSocketSession session : sessions) {
            sendAndClose(session, gameRoomId, reason, action);
        }
    }

    private void sendAndClose(GameRoomWebSocketSession session, Long gameRoomId, String reason, String action) {
        WebSocketSession webSocketSession = session.getWebSocketSession();
        try {
            if (webSocketSession.isOpen()) {
                webSocketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        GameWebSocketServerMessage.gameWaitingTimeout(
                                gameRoomId,
                                reason,
                                action
                        )
                )));
            }
        } catch (Exception e) {
            log.warn("Failed to send game waiting timeout WebSocket message. gameRoomId={}, sessionId={}",
                    gameRoomId, session.getSessionId(), e);
        } finally {
            sessionRegistry.unregister(session.getSessionId());
            closeQuietly(webSocketSession);
        }
    }

    private void closeQuietly(WebSocketSession webSocketSession) {
        try {
            if (webSocketSession.isOpen()) {
                webSocketSession.close(CloseStatus.NORMAL);
            }
        } catch (Exception e) {
            log.warn("Failed to close game waiting timeout WebSocket session. sessionId={}",
                    webSocketSession.getId(), e);
        }
    }
}
