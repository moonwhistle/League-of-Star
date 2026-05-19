package com.sang.smite.game.websocket.dto;

/**
 * 서버가 게임 대기 WebSocket으로 보내는 공통 메시지 envelope입니다.
 */
public record GameWebSocketServerMessage(
        GameWebSocketMessageType type,
        Object payload
) {

    private static final String ERROR_INVALID_MESSAGE_TYPE = "INVALID_MESSAGE_TYPE";

    public static GameWebSocketServerMessage playerJoined(Long userId) {
        return new GameWebSocketServerMessage(
                GameWebSocketMessageType.PLAYER_JOINED,
                new PlayerPayload(userId)
        );
    }

    public static GameWebSocketServerMessage playerReady(Long userId, boolean bothReady) {
        return new GameWebSocketServerMessage(
                GameWebSocketMessageType.PLAYER_READY,
                new PlayerReadyPayload(userId, bothReady)
        );
    }

    public static GameWebSocketServerMessage playerLeft(Long userId) {
        return new GameWebSocketServerMessage(
                GameWebSocketMessageType.PLAYER_LEFT,
                new PlayerPayload(userId)
        );
    }

    public static GameWebSocketServerMessage rttPing(int seq) {
        return new GameWebSocketServerMessage(
                GameWebSocketMessageType.RTT_PING,
                new RttPingPayload(seq)
        );
    }

    public static GameWebSocketServerMessage gameWaitingTimeout(Long gameRoomId, String reason, String action) {
        return new GameWebSocketServerMessage(
                GameWebSocketMessageType.GAME_WAITING_TIMEOUT,
                new GameWaitingTimeoutPayload(gameRoomId, reason, action)
        );
    }

    public static GameWebSocketServerMessage invalidMessageType() {
        return error(ERROR_INVALID_MESSAGE_TYPE, "Unsupported WebSocket message type.");
    }

    public static GameWebSocketServerMessage error(String code, String reason) {
        return new GameWebSocketServerMessage(
                GameWebSocketMessageType.ERROR,
                new ErrorPayload(code, reason)
        );
    }

    public record PlayerPayload(Long userId) {
    }

    public record PlayerReadyPayload(Long userId, boolean bothReady) {
    }

    public record GameWaitingTimeoutPayload(Long gameRoomId, String reason, String action) {
    }

    public record RttPingPayload(int seq) {
    }

    public record ErrorPayload(String code, String reason) {
    }
}
