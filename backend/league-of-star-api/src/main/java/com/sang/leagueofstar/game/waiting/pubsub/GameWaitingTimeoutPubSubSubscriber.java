package com.sang.leagueofstar.game.waiting.pubsub;

import com.sang.leagueofstar.game.waiting.pubsub.dto.GameWaitingTimeoutPubSubMessage;
import com.sang.leagueofstar.game.waiting.pubsub.util.GameWaitingTimeoutPubSubMessageCodec;
import com.sang.leagueofstar.game.websocket.service.GameWaitingTimeoutWebSocketSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub game waiting timeout 메시지를 현재 인스턴스의 WebSocket 연결로 전달합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWaitingTimeoutPubSubSubscriber implements MessageListener {

    private final GameWaitingTimeoutPubSubMessageCodec messageCodec;
    private final GameWaitingTimeoutWebSocketSender webSocketSender;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            GameWaitingTimeoutPubSubMessage pubSubMessage = messageCodec.decode(payload);
            webSocketSender.sendTimeout(
                    pubSubMessage.gameRoomId(),
                    pubSubMessage.reason(),
                    pubSubMessage.action()
            );
        } catch (Exception e) {
            log.error("Failed to dispatch game waiting timeout Pub/Sub message. payload={}", payload, e);
        }
    }
}
