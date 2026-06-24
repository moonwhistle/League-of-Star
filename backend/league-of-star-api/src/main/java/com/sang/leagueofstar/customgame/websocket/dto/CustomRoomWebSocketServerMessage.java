package com.sang.leagueofstar.customgame.websocket.dto;

import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomGameStartResponse;

/**
 * 서버가 Custom Room WebSocket으로 보내는 공통 메시지 envelope입니다.
 */
public record CustomRoomWebSocketServerMessage(
        CustomRoomWebSocketMessageType type,
        Object payload
) {

    private static final String ERROR_INVALID_MESSAGE_TYPE = "INVALID_MESSAGE_TYPE";

    public static CustomRoomWebSocketServerMessage roomUpdated(CustomRoomResponse payload) {
        return new CustomRoomWebSocketServerMessage(
                CustomRoomWebSocketMessageType.ROOM_UPDATED,
                payload
        );
    }

    public static CustomRoomWebSocketServerMessage roomClosed(CustomRoomResponse payload) {
        return new CustomRoomWebSocketServerMessage(
                CustomRoomWebSocketMessageType.ROOM_CLOSED,
                payload
        );
    }

    public static CustomRoomWebSocketServerMessage roomStarted(CustomGameStartResponse payload) {
        return new CustomRoomWebSocketServerMessage(
                CustomRoomWebSocketMessageType.ROOM_STARTED,
                payload
        );
    }

    public static CustomRoomWebSocketServerMessage invalidMessageType() {
        return error(
                ERROR_INVALID_MESSAGE_TYPE,
                "Custom Room WebSocket does not accept client messages in this issue."
        );
    }

    public static CustomRoomWebSocketServerMessage error(String code, String reason) {
        return new CustomRoomWebSocketServerMessage(
                CustomRoomWebSocketMessageType.ERROR,
                new ErrorPayload(code, reason)
        );
    }

    public record ErrorPayload(String code, String reason) {
    }
}
