package com.sang.smite.game.websocket.session;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * WebSocket handshake에서 session attributes에 저장할 key를 관리한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameWebSocketSessionAttribute {

    public static final String GAME_ROOM_ID = "gameRoomId";
    public static final String USER_ID = "userId";
}
