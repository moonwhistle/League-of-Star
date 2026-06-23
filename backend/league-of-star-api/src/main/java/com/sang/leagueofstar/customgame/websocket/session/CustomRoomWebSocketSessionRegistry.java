package com.sang.leagueofstar.customgame.websocket.session;

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
 * Custom Room WebSocket 연결 상태를 API 인스턴스 local memory에서 관리한다.
 * 참가자 상태 source of truth는 DB이며, 이 registry는 연결된 session만 관리한다.
 */
@Slf4j
@Component
public class CustomRoomWebSocketSessionRegistry {

    private final Map<Long, Map<Long, CustomRoomWebSocketSession>> sessionsByRoom = new HashMap<>();
    private final Map<String, CustomRoomWebSocketSession> sessionsById = new HashMap<>();

    /**
     * custom room 참가자의 WebSocket 연결을 등록한다. 같은 room/user의 기존 연결은 새 연결로 교체한다.
     */
    public void register(Long customRoomId, Long userId, WebSocketSession webSocketSession) {
        Objects.requireNonNull(customRoomId, "customRoomId must not be null.");
        Objects.requireNonNull(userId, "userId must not be null.");
        Objects.requireNonNull(webSocketSession, "webSocketSession must not be null.");

        CustomRoomWebSocketSession sessionToClose;
        synchronized (this) {
            removeIfAlreadyRegistered(webSocketSession.getId());

            Map<Long, CustomRoomWebSocketSession> roomSessions = sessionsByRoom.computeIfAbsent(
                    customRoomId,
                    ignored -> new HashMap<>()
            );
            CustomRoomWebSocketSession previousSession = roomSessions.remove(userId);
            if (previousSession != null) {
                sessionsById.remove(previousSession.getSessionId());
            }

            CustomRoomWebSocketSession newSession = CustomRoomWebSocketSession.of(
                    customRoomId,
                    userId,
                    webSocketSession
            );
            roomSessions.put(userId, newSession);
            sessionsById.put(newSession.getSessionId(), newSession);
            sessionToClose = previousSession;
        }

        if (sessionToClose != null) {
            closeQuietly(sessionToClose.getWebSocketSession());
        }
    }

    /**
     * WebSocket sessionId 기준으로 등록된 연결을 제거한다. DB participant 상태는 변경하지 않는다.
     */
    public synchronized void unregister(String sessionId) {
        CustomRoomWebSocketSession removedSession = sessionsById.remove(sessionId);
        if (removedSession == null) {
            return;
        }

        Map<Long, CustomRoomWebSocketSession> roomSessions = sessionsByRoom.get(removedSession.getCustomRoomId());
        if (roomSessions == null) {
            return;
        }

        CustomRoomWebSocketSession currentSession = roomSessions.get(removedSession.getUserId());
        if (currentSession != null && currentSession.getSessionId().equals(sessionId)) {
            roomSessions.remove(removedSession.getUserId());
        }
        if (roomSessions.isEmpty()) {
            sessionsByRoom.remove(removedSession.getCustomRoomId());
        }
    }

    /**
     * WebSocket sessionId로 연결 상태를 조회한다.
     */
    public synchronized Optional<CustomRoomWebSocketSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    /**
     * customRoomId와 userId로 연결 상태를 조회한다.
     */
    public synchronized Optional<CustomRoomWebSocketSession> findByRoomAndUser(Long customRoomId, Long userId) {
        Map<Long, CustomRoomWebSocketSession> roomSessions = sessionsByRoom.get(customRoomId);
        if (roomSessions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomSessions.get(userId));
    }

    /**
     * customRoom에 연결된 모든 session 상태의 복사본을 반환한다.
     */
    public synchronized List<CustomRoomWebSocketSession> findByRoom(Long customRoomId) {
        Map<Long, CustomRoomWebSocketSession> roomSessions = sessionsByRoom.get(customRoomId);
        if (roomSessions == null) {
            return List.of();
        }
        return new ArrayList<>(roomSessions.values());
    }

    /**
     * customRoomId/userId 기준으로 session을 닫고 registry에서 제거한다. HTTP leave 이후 호출하는 용도다.
     */
    public void closeAndUnregister(Long customRoomId, Long userId) {
        CustomRoomWebSocketSession sessionToClose;
        synchronized (this) {
            sessionToClose = findByRoomAndUser(customRoomId, userId).orElse(null);
            if (sessionToClose != null) {
                unregister(sessionToClose.getSessionId());
            }
        }

        if (sessionToClose != null) {
            closeQuietly(sessionToClose.getWebSocketSession());
        }
    }

    /**
     * customRoomId 기준으로 모든 session을 닫고 registry에서 제거한다. room close 이후 호출하는 용도다.
     */
    public void closeAndUnregisterRoom(Long customRoomId) {
        List<CustomRoomWebSocketSession> sessionsToClose;
        synchronized (this) {
            sessionsToClose = findByRoom(customRoomId);
            for (CustomRoomWebSocketSession session : sessionsToClose) {
                unregister(session.getSessionId());
            }
        }

        for (CustomRoomWebSocketSession session : sessionsToClose) {
            closeQuietly(session.getWebSocketSession());
        }
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
            log.warn("Failed to close custom room WebSocket session. sessionId={}", webSocketSession.getId(), e);
        }
    }
}
