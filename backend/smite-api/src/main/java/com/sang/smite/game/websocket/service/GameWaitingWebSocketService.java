package com.sang.smite.game.websocket.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.game.end.service.GameEndScheduleService;
import com.sang.smite.game.rtt.common.constant.GameRttConstants;
import com.sang.smite.game.rtt.domain.GameRttPongResult;
import com.sang.smite.game.rtt.service.GameRttMeasurementService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import com.sang.smite.game.smite.domain.GameSmiteFailureReason;
import com.sang.smite.game.smite.dto.GameResultPayload;
import com.sang.smite.game.smite.dto.GameSmiteHandleResponse;
import com.sang.smite.game.smite.dto.SmiteResultPayload;
import com.sang.smite.game.smite.service.GameSmiteService;
import com.sang.smite.game.smite.service.GameSmiteWebSocketSender;
import com.sang.smite.game.start.domain.GameStartFailureReason;
import com.sang.smite.game.start.domain.GameStartTransitionResult;
import com.sang.smite.game.start.dto.GameStartScenarioPayload;
import com.sang.smite.game.start.service.GameStartFailureProcessor;
import com.sang.smite.game.start.service.GameStartScenarioService;
import com.sang.smite.game.start.service.GameStartTransitionService;
import com.sang.smite.game.websocket.dto.GameWebSocketClientMessage;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.service.GameWaitingReadyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameWaitingWebSocketService {

    private final GameRoomWebSocketSessionRegistry sessionRegistry;
    private final GameWaitingReadyService gameWaitingReadyService;
    private final GameRttMeasurementService gameRttMeasurementService;
    private final GameRoomWebSocketMessageSender messageSender;
    private final GameStartScenarioService gameStartScenarioService;
    private final GameStartTransitionService gameStartTransitionService;
    private final GameStartFailureProcessor gameStartFailureProcessor;
    private final GameEndScheduleService gameEndScheduleService;
    private final GameStartWebSocketSender gameStartWebSocketSender;
    private final GameSmiteService gameSmiteService;
    private final GameSmiteWebSocketSender gameSmiteWebSocketSender;

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
        OptionalInt seq = clientMessage.rttSeq();
        if (seq.isEmpty()) {
            return;
        }

        GameRttPongResult pongResult;
        try {
            pongResult = gameRttMeasurementService.recordPong(
                    currentSession.getGameRoomId(),
                    currentSession.getUserId(),
                    seq.getAsInt()
            );
        } catch (Exception e) {
            gameRttMeasurementService.failMeasurement(currentSession.getGameRoomId(), currentSession.getUserId());
            log.warn("Failed to process RTT_PONG. gameRoomId={}, userId={}",
                    currentSession.getGameRoomId(), currentSession.getUserId(), e);
            return;
        }

        try {
            if (pongResult.needsNextPing()) {
                sendRttPing(currentSession, pongResult.nextSeq());
                return;
            }
            if (pongResult.completed() && pongResult.passed()) {
                startGameIfReady(currentSession.getGameRoomId());
            }
        } catch (IOException e) {
            gameRttMeasurementService.failMeasurement(currentSession.getGameRoomId(), currentSession.getUserId());
            log.warn("Failed to send RTT_PING. gameRoomId={}, userId={}",
                    currentSession.getGameRoomId(), currentSession.getUserId(), e);
        }
    }

    public void handleSmite(GameRoomWebSocketSession currentSession, long serverReceiveTimeMs) {
        try {
            gameSmiteService.handleSmite(new GameSmiteCommand(
                            currentSession.getGameRoomId(),
                            currentSession.getUserId(),
                            serverReceiveTimeMs
                    ))
                    .ifPresent(response -> sendSmiteResponse(currentSession, response));
        } catch (CoreException e) {
            sendSmiteError(currentSession, mapSmiteFailureReason(e), e.getErrorCode().message());
        } catch (DataAccessException e) {
            log.warn("Failed to persist SMITE. gameRoomId={}, userId={}",
                    currentSession.getGameRoomId(), currentSession.getUserId(), e);
            sendSmiteError(
                    currentSession,
                    GameSmiteFailureReason.SMITE_PROCESSING_FAILED,
                    "Failed to process SMITE."
            );
        }
    }

    private void sendSmiteResponse(GameRoomWebSocketSession currentSession,
                                   GameSmiteHandleResponse response) {
        response.smiteResultOptional()
                .ifPresent(smiteResult -> sendSmiteResult(currentSession, smiteResult));
        response.gameResultOptional()
                .ifPresent(gameResult -> broadcastGameResult(currentSession, gameResult));
    }

    private void sendSmiteResult(GameRoomWebSocketSession currentSession,
                                 SmiteResultPayload payload) {
        try {
            gameSmiteWebSocketSender.sendSmiteResult(currentSession.getWebSocketSession(), payload);
        } catch (IOException e) {
            log.warn("Failed to send SMITE_RESULT. gameRoomId={}, userId={}",
                    currentSession.getGameRoomId(), currentSession.getUserId(), e);
        }
    }

    private void broadcastGameResult(GameRoomWebSocketSession currentSession,
                                     GameResultPayload payload) {
        try {
            gameSmiteWebSocketSender.broadcastGameResult(currentSession.getGameRoomId(), payload);
        } catch (IOException e) {
            log.warn("Failed to broadcast GAME_RESULT. gameRoomId={}", currentSession.getGameRoomId(), e);
        }
    }

    private void sendSmiteError(GameRoomWebSocketSession currentSession,
                                GameSmiteFailureReason reason,
                                String message) {
        try {
            messageSender.send(
                    currentSession.getWebSocketSession(),
                    GameWebSocketServerMessage.error(reason.getCode(), message)
            );
        } catch (IOException e) {
            log.warn("Failed to send SMITE ERROR. gameRoomId={}, userId={}",
                    currentSession.getGameRoomId(), currentSession.getUserId(), e);
        }
    }

    private GameSmiteFailureReason mapSmiteFailureReason(CoreException exception) {
        if (exception.getErrorCode().equals(CoreErrorCode.INVALID_GAME_PARTICIPANTS)) {
            return GameSmiteFailureReason.NOT_GAME_PARTICIPANT;
        }
        return GameSmiteFailureReason.INVALID_SMITE_STATE;
    }

    public void cleanupSession(WebSocketSession session) {
        sessionRegistry.findBySessionId(session.getId())
                .ifPresent(this::cleanupSession);
    }

    private void cleanupSession(GameRoomWebSocketSession currentSession) {
        gameRttMeasurementService.failMeasurement(currentSession.getGameRoomId(), currentSession.getUserId());
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
            sendRttPing(session, GameRttConstants.INITIAL_RTT_SEQUENCE);
        }
    }

    private void startGameIfReady(Long gameRoomId) {
        GameStartTransitionResult transitionResult = gameStartTransitionService.transitionToInProgress(gameRoomId);
        if (!transitionResult.started()) {
            return;
        }

        GameStartScenarioPayload scenario;
        try {
            scenario = gameStartScenarioService.getScenarioPayload(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to load game start scenario. gameRoomId={}", gameRoomId, e);
            gameStartFailureProcessor.processStartedFailure(
                    gameRoomId,
                    GameStartFailureReason.SCENARIO_LOAD_FAILED,
                    false
            );
            return;
        }

        try {
            gameEndScheduleService.registerEndDeadline(
                    gameRoomId,
                    transitionResult.startAtMillis(),
                    scenario.durationMs()
            );
        } catch (RuntimeException e) {
            log.warn("Failed to register game end deadline. gameRoomId={}", gameRoomId, e);
            gameStartFailureProcessor.processStartedFailure(
                    gameRoomId,
                    GameStartFailureReason.GAME_END_DEADLINE_REGISTRATION_FAILED,
                    true
            );
            return;
        }

        boolean sent = gameStartWebSocketSender.sendStart(
                gameRoomId,
                transitionResult.serverTimeMillis(),
                transitionResult.startAtMillis(),
                scenario
        );
        if (!sent) {
            gameStartFailureProcessor.processStartedFailure(
                    gameRoomId,
                    GameStartFailureReason.GAME_START_MESSAGE_SEND_FAILED,
                    true
            );
        }
    }

    private void sendRttPing(GameRoomWebSocketSession session, int seq) throws IOException {
        messageSender.send(
                session.getWebSocketSession(),
                GameWebSocketServerMessage.rttPing(seq)
        );
        gameRttMeasurementService.recordPingSent(
                session.getGameRoomId(),
                session.getUserId(),
                seq
        );
    }
}
