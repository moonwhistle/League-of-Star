package com.sang.smite.game.websocket.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.OptionalInt;

/**
 * 클라이언트가 게임 대기 WebSocket으로 보내는 공통 메시지 envelope입니다.
 */
public record GameWebSocketClientMessage(
        GameWebSocketMessageType type,
        JsonNode payload
) {

    public boolean isClientReady() {
        return type == GameWebSocketMessageType.CLIENT_READY;
    }

    public boolean isRttPong() {
        return type == GameWebSocketMessageType.RTT_PONG;
    }

    public boolean isSmite() {
        return type == GameWebSocketMessageType.SMITE;
    }

    public boolean hasInvalidSmitePayload() {
        return isSmite()
                && payload != null
                && !payload.isNull()
                && (!payload.isObject() || !payload.isEmpty());
    }

    public OptionalInt rttSeq() {
        if (payload == null || !payload.has("seq") || !payload.get("seq").canConvertToInt()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(payload.get("seq").asInt());
    }
}
