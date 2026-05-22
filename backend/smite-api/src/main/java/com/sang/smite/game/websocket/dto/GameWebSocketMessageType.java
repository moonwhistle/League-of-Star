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
    RTT_PONG(Direction.CLIENT),
    SMITE(Direction.CLIENT),
    PLAYER_JOINED(Direction.SERVER),
    PLAYER_READY(Direction.SERVER),
    PLAYER_LEFT(Direction.SERVER),
    RTT_PING(Direction.SERVER),
    GAME_WAITING_TIMEOUT(Direction.SERVER),
    GAME_START_FAILED(Direction.SERVER),
    COUNTDOWN(Direction.SERVER),
    GAME_START(Direction.SERVER),
    GAME_RESULT(Direction.SERVER),
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
