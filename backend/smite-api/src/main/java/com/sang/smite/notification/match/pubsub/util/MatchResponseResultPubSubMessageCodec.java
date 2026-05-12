package com.sang.smite.notification.match.pubsub.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import com.sang.smite.notification.match.pubsub.exception.MatchResponseResultPubSubMessageCodecException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * match_response_result Pub/Sub 메시지를 JSON 문자열로 변환합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchResponseResultPubSubMessageCodec {

    private final ObjectMapper objectMapper;

    public String encode(MatchResponseResultPubSubMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new MatchResponseResultPubSubMessageCodecException("match_response_result Pub/Sub 메시지 직렬화 실패", e);
        }
    }

    public MatchResponseResultPubSubMessage decode(String payload) {
        try {
            return objectMapper.readValue(payload, MatchResponseResultPubSubMessage.class);
        } catch (JsonProcessingException e) {
            throw new MatchResponseResultPubSubMessageCodecException("match_response_result Pub/Sub 메시지 역직렬화 실패", e);
        }
    }
}
