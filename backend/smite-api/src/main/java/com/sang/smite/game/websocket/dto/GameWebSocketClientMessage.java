package com.sang.smite.game.websocket.dto;

import com.fasterxml.jackson.databind.JsonNode;

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
}
