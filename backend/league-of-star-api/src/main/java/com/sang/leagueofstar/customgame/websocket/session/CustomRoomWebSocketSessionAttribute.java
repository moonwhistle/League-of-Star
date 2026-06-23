package com.sang.leagueofstar.customgame.websocket.session;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Custom Room WebSocket handshake에서 session attributes에 저장할 key를 관리한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CustomRoomWebSocketSessionAttribute {

    public static final String CUSTOM_ROOM_ID = "customRoomId";
    public static final String USER_ID = "userId";
}
