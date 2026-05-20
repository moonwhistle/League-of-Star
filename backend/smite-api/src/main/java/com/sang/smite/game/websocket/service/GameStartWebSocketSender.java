package com.sang.smite.game.websocket.service;

import com.sang.smite.game.start.common.constant.GameStartConstants;
import com.sang.smite.game.start.dto.GameStartScenarioPayload;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameStartWebSocketSender {

    private final GameRoomWebSocketMessageSender messageSender;

    public void sendStart(Long gameRoomId,
                          long serverTime,
                          long startAt,
                          GameStartScenarioPayload scenario) {
        try {
            messageSender.broadcast(
                    gameRoomId,
                    GameWebSocketServerMessage.countdown(
                            gameRoomId,
                            serverTime,
                            startAt,
                            GameStartConstants.COUNTDOWN_DISPLAY_SECONDS
                    )
            );
            messageSender.broadcast(
                    gameRoomId,
                    GameWebSocketServerMessage.gameStart(gameRoomId, serverTime, startAt, scenario)
            );
        } catch (IOException e) {
            log.warn("Failed to send game start WebSocket messages. gameRoomId={}", gameRoomId, e);
        }
    }
}
