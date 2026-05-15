package com.sang.smite.game.websocket.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게임 대기 WebSocket에서 주고받는 메시지 type입니다.
 */
@Getter
@RequiredArgsConstructor
public enum GameWebSocketMessageType {

    CLIENT_READY(Direction.CLIENT),
    PLAYER_JOINED(Direction.SERVER),
    PLAYER_READY(Direction.SERVER),
    PLAYER_LEFT(Direction.SERVER),
    ERROR(Direction.SERVER);

    private final Direction direction;

    public boolean isClientMessage() {
        return direction == Direction.CLIENT;
    }

    public boolean isServerMessage() {
        return direction == Direction.SERVER;
    }

    public enum Direction {
        CLIENT,
        SERVER
    }
}
