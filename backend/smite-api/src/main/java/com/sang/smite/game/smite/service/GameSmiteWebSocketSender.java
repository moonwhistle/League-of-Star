package com.sang.smite.game.smite.service;

import com.sang.smite.game.smite.dto.GameResultPayload;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import com.sang.smite.game.websocket.service.GameRoomWebSocketMessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GameSmiteWebSocketSender {

    private final GameRoomWebSocketMessageSender messageSender;

    public void sendGameResult(WebSocketSession session, GameResultPayload payload) throws IOException {
        messageSender.send(session, GameWebSocketServerMessage.gameResult(payload));
    }

    public void broadcastGameResult(Long gameRoomId, GameResultPayload payload) throws IOException {
        messageSender.broadcast(gameRoomId, GameWebSocketServerMessage.gameResult(payload));
    }
}
