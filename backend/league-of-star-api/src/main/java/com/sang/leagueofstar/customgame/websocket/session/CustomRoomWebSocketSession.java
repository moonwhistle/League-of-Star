package com.sang.leagueofstar.customgame.websocket.session;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

/**
 * custom room에 연결된 사용자 WebSocket session을 나타낸다.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomRoomWebSocketSession {

    private final Long customRoomId;
    private final Long userId;
    private final String sessionId;
    private final WebSocketSession webSocketSession;

    public static CustomRoomWebSocketSession of(
            Long customRoomId,
            Long userId,
            WebSocketSession webSocketSession
    ) {
        return new CustomRoomWebSocketSession(customRoomId, userId, webSocketSession.getId(), webSocketSession);
    }
}
