package com.sang.leagueofstar.game.waiting.pubsub.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.game.waiting.pubsub.dto.GameWaitingTimeoutPubSubMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * game waiting timeout Pub/Sub 메시지를 JSON 문자열로 변환합니다.
 */
@Component
@RequiredArgsConstructor
public class GameWaitingTimeoutPubSubMessageCodec {

    private final ObjectMapper objectMapper;

    public String encode(GameWaitingTimeoutPubSubMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("game waiting timeout Pub/Sub 메시지 직렬화 실패", e);
        }
    }

    public GameWaitingTimeoutPubSubMessage decode(String payload) {
        try {
            return objectMapper.readValue(payload, GameWaitingTimeoutPubSubMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("game waiting timeout Pub/Sub 메시지 역직렬화 실패", e);
        }
    }
}
