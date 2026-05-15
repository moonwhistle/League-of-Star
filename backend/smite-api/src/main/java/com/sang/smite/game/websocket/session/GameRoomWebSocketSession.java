package com.sang.smite.game.websocket.session;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

/**
 * gameRoom에 연결된 사용자 WebSocket session과 대기 READY 상태를 나타낸다.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class GameRoomWebSocketSession {

    private final Long gameRoomId;
    private final Long userId;
    private final String sessionId;
    private final WebSocketSession webSocketSession;
    private boolean ready;

    public static GameRoomWebSocketSession of(Long gameRoomId, Long userId, WebSocketSession webSocketSession) {
        return new GameRoomWebSocketSession(gameRoomId, userId, webSocketSession.getId(), webSocketSession);
    }

    public void markReady() {
        this.ready = true;
    }
}
