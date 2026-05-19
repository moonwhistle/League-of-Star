package com.sang.smite.game.websocket.service;

import com.sang.smite.game.rtt.common.constant.GameRttConstants;
import com.sang.smite.game.rtt.service.GameRttMeasurementService;
import com.sang.smite.game.websocket.dto.GameWebSocketClientMessage;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.service.GameWaitingReadyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameWaitingWebSocketService {

    private final GameRoomWebSocketSessionRegistry sessionRegistry;
    private final GameWaitingReadyService gameWaitingReadyService;
    private final GameRttMeasurementService gameRttMeasurementService;
    private final GameRoomWebSocketMessageSender messageSender;

    public void registerSession(Long gameRoomId, Long userId, WebSocketSession session) throws IOException {
        sessionRegistry.register(gameRoomId, userId, session);
        messageSender.broadcast(gameRoomId, GameWebSocketServerMessage.playerJoined(userId));
    }

    public void handleClientReady(GameRoomWebSocketSession currentSession) throws IOException {
        Long gameRoomId = currentSession.getGameRoomId();
        Long userId = currentSession.getUserId();
        GameWaitingReadyResult readyResult = gameWaitingReadyService.markReady(gameRoomId, userId);
        if (!readyResult.accepted()) {
            closeRejectedSession(currentSession);
            return;
        }

        sessionRegistry.markReady(gameRoomId, userId);
        messageSender.broadcast(gameRoomId, GameWebSocketServerMessage.playerReady(userId, readyResult.bothReady()));
        if (readyResult.bothReady() && sessionRegistry.areBothReady(gameRoomId)) {
            startRttMeasurement(gameRoomId);
        }
    }

    public void handleRttPong(GameRoomWebSocketSession currentSession, GameWebSocketClientMessage clientMessage) {
        // RTT sample 계산과 다음 ping 전송은 RTT_PONG 처리 단계에서 연결한다.
    }

    public void cleanupSession(WebSocketSession session) {
        sessionRegistry.findBySessionId(session.getId())
                .ifPresent(this::cleanupSession);
    }

    private void cleanupSession(GameRoomWebSocketSession currentSession) {
        sessionRegistry.unregister(currentSession.getSessionId());
        try {
            messageSender.broadcast(
                    currentSession.getGameRoomId(),
                    GameWebSocketServerMessage.playerLeft(currentSession.getUserId())
            );
        } catch (IOException e) {
            log.warn("Failed to broadcast player left message. sessionId={}", currentSession.getSessionId(), e);
        }
    }

    private void closeRejectedSession(GameRoomWebSocketSession currentSession) throws IOException {
        WebSocketSession webSocketSession = currentSession.getWebSocketSession();
        sessionRegistry.unregister(webSocketSession.getId());
        if (webSocketSession.isOpen()) {
            webSocketSession.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private void startRttMeasurement(Long gameRoomId) throws IOException {
        List<GameRoomWebSocketSession> sessions = sessionRegistry.findByRoom(gameRoomId);
        boolean started = gameRttMeasurementService.startMeasurement(
                gameRoomId,
                sessions.stream()
                        .map(GameRoomWebSocketSession::getUserId)
                        .toList()
        );
        if (!started) {
            return;
        }

        for (GameRoomWebSocketSession session : sessions) {
            messageSender.send(
                    session.getWebSocketSession(),
                    GameWebSocketServerMessage.rttPing(GameRttConstants.INITIAL_RTT_SEQUENCE)
            );
            gameRttMeasurementService.recordPingSent(
                    gameRoomId,
                    session.getUserId(),
                    GameRttConstants.INITIAL_RTT_SEQUENCE
            );
        }
    }
}
