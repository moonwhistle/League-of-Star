package com.sang.leagueofstar.customgame.websocket.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Custom Room WebSocket에서 서버가 보내는 메시지 type입니다.
 */
@Getter
@RequiredArgsConstructor
public enum CustomRoomWebSocketMessageType {

    ROOM_UPDATED(Direction.SERVER),
    ROOM_CLOSED(Direction.SERVER),
    ERROR(Direction.SERVER);

    private final Direction direction;

    public boolean isServerMessage() {
        return direction == Direction.SERVER;
    }

    public enum Direction {
        SERVER
    }
}
