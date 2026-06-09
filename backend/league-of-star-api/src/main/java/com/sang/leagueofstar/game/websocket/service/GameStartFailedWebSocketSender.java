package com.sang.leagueofstar.game.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.leagueofstar.game.websocket.session.GameRoomWebSocketSession;
import com.sang.leagueofstar.game.websocket.session.GameRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameStartFailedWebSocketSender {

    private final ObjectMapper objectMapper;
    private final GameRoomWebSocketSessionRegistry sessionRegistry;

    public void sendFailure(Long gameRoomId, String reason, String action) {
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
                        GameWebSocketServerMessage.gameStartFailed(
                                gameRoomId,
                                reason,
                                action
                        )
                )));
            }
        } catch (Exception e) {
            log.warn("Failed to send game start failed WebSocket message. gameRoomId={}, sessionId={}",
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
            log.warn("Failed to close game start failed WebSocket session. sessionId={}",
                    webSocketSession.getId(), e);
        }
    }
}
