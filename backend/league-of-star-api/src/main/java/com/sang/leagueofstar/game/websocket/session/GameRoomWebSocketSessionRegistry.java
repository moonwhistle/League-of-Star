package com.sang.leagueofstar.game.websocket.session;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 게임 대기 WebSocket 연결 상태를 API 인스턴스 local memory에서 관리한다.
 * 멀티 인스턴스 환경에서는 같은 gameRoomId가 같은 인스턴스로 라우팅되어야 한다.
 */
@Slf4j
@Component
public class GameRoomWebSocketSessionRegistry {

    private static final int REQUIRED_PARTICIPANT_COUNT = GameRoom.MAX_PARTICIPANTS;

    private final Map<Long, Map<Long, GameRoomWebSocketSession>> sessionsByRoom = new HashMap<>();
    private final Map<String, GameRoomWebSocketSession> sessionsById = new HashMap<>();

    /**
     * gameRoom 참가자의 WebSocket 연결을 등록한다. 같은 유저의 기존 연결은 새 연결로 교체한다.
     */
    public void register(Long gameRoomId, Long userId, WebSocketSession webSocketSession) {
        Objects.requireNonNull(gameRoomId, "gameRoomId must not be null.");
        Objects.requireNonNull(userId, "userId must not be null.");
        Objects.requireNonNull(webSocketSession, "webSocketSession must not be null.");

        GameRoomWebSocketSession sessionToClose;
        synchronized (this) {
            removeIfAlreadyRegistered(webSocketSession.getId());

            Map<Long, GameRoomWebSocketSession> roomSessions = sessionsByRoom.computeIfAbsent(
                    gameRoomId,
                    ignored -> new HashMap<>()
            );
            GameRoomWebSocketSession previousSession = roomSessions.remove(userId);
            if (previousSession != null) {
                sessionsById.remove(previousSession.getSessionId());
            }

            GameRoomWebSocketSession newSession = GameRoomWebSocketSession.of(gameRoomId, userId, webSocketSession);
            roomSessions.put(userId, newSession);
            sessionsById.put(newSession.getSessionId(), newSession);
            sessionToClose = previousSession;
        }

        if (sessionToClose != null) {
            closeQuietly(sessionToClose.getWebSocketSession());
        }
    }

    /**
     * WebSocket sessionId 기준으로 등록된 연결을 제거한다.
     */
    public synchronized void unregister(String sessionId) {
        GameRoomWebSocketSession removedSession = sessionsById.remove(sessionId);
        if (removedSession == null) {
            return;
        }

        Map<Long, GameRoomWebSocketSession> roomSessions = sessionsByRoom.get(removedSession.getGameRoomId());
        if (roomSessions == null) {
            return;
        }

        GameRoomWebSocketSession currentSession = roomSessions.get(removedSession.getUserId());
        if (currentSession != null && currentSession.getSessionId().equals(sessionId)) {
            roomSessions.remove(removedSession.getUserId());
        }
        if (roomSessions.isEmpty()) {
            sessionsByRoom.remove(removedSession.getGameRoomId());
        }
    }

    /**
     * WebSocket sessionId로 연결 상태를 조회한다.
     */
    public synchronized Optional<GameRoomWebSocketSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    /**
     * gameRoomId와 userId로 연결 상태를 조회한다.
     */
    public synchronized Optional<GameRoomWebSocketSession> findByRoomAndUser(Long gameRoomId, Long userId) {
        Map<Long, GameRoomWebSocketSession> roomSessions = sessionsByRoom.get(gameRoomId);
        if (roomSessions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomSessions.get(userId));
    }

    /**
     * gameRoom에 연결된 모든 session 상태의 복사본을 반환한다.
     */
    public synchronized List<GameRoomWebSocketSession> findByRoom(Long gameRoomId) {
        Map<Long, GameRoomWebSocketSession> roomSessions = sessionsByRoom.get(gameRoomId);
        if (roomSessions == null) {
            return List.of();
        }
        return new ArrayList<>(roomSessions.values());
    }

    /**
     * 특정 사용자가 gameRoom에 WebSocket으로 연결되어 있는지 확인한다.
     */
    public synchronized boolean isConnected(Long gameRoomId, Long userId) {
        return findByRoomAndUser(gameRoomId, userId).isPresent();
    }

    /**
     * gameRoom의 두 참가자가 모두 WebSocket으로 연결되었는지 확인한다.
     */
    public synchronized boolean areBothConnected(Long gameRoomId) {
        Map<Long, GameRoomWebSocketSession> roomSessions = sessionsByRoom.get(gameRoomId);
        return roomSessions != null && roomSessions.size() == REQUIRED_PARTICIPANT_COUNT;
    }

    /**
     * 연결된 사용자의 대기 READY 상태를 기록한다.
     */
    public synchronized boolean markReady(Long gameRoomId, Long userId) {
        Optional<GameRoomWebSocketSession> session = findByRoomAndUser(gameRoomId, userId);
        session.ifPresent(GameRoomWebSocketSession::markReady);
        return session.isPresent();
    }

    /**
     * gameRoom의 두 참가자가 모두 연결되어 있고 READY 상태인지 확인한다.
     */
    public synchronized boolean areBothReady(Long gameRoomId) {
        Map<Long, GameRoomWebSocketSession> roomSessions = sessionsByRoom.get(gameRoomId);
        return roomSessions != null
                && roomSessions.size() == REQUIRED_PARTICIPANT_COUNT
                && roomSessions.values().stream().allMatch(GameRoomWebSocketSession::isReady);
    }

    private void removeIfAlreadyRegistered(String sessionId) {
        if (sessionsById.containsKey(sessionId)) {
            unregister(sessionId);
        }
    }

    private void closeQuietly(WebSocketSession webSocketSession) {
        try {
            if (webSocketSession.isOpen()) {
                webSocketSession.close(CloseStatus.NORMAL);
            }
        } catch (IOException e) {
            log.warn("Failed to close duplicate game WebSocket session. sessionId={}", webSocketSession.getId(), e);
        }
    }
}
