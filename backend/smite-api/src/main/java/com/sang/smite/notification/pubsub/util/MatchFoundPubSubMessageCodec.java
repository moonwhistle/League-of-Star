package com.sang.smite.notification.pubsub.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.notification.pubsub.dto.MatchFoundPubSubMessage;
import com.sang.smite.notification.pubsub.exception.MatchFoundPubSubMessageCodecException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * match_found Pub/Sub 메시지를 JSON 문자열로 변환합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchFoundPubSubMessageCodec {

    private final ObjectMapper objectMapper;

    public String encode(MatchFoundPubSubMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new MatchFoundPubSubMessageCodecException("match_found Pub/Sub 메시지 직렬화 실패", e);
        }
    }

    public MatchFoundPubSubMessage decode(String payload) {
        try {
            return objectMapper.readValue(payload, MatchFoundPubSubMessage.class);
        } catch (JsonProcessingException e) {
            throw new MatchFoundPubSubMessageCodecException("match_found Pub/Sub 메시지 역직렬화 실패", e);
        }
    }
}
