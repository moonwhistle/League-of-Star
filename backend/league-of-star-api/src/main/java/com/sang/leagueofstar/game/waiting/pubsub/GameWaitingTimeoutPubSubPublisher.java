package com.sang.leagueofstar.game.waiting.pubsub;

import com.sang.leagueofstar.game.waiting.common.constant.GameWaitingConstants;
import com.sang.leagueofstar.game.waiting.pubsub.dto.GameWaitingTimeoutPubSubMessage;
import com.sang.leagueofstar.game.waiting.pubsub.util.GameWaitingTimeoutPubSubMessageCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * game waiting timeout 메시지를 Redis Pub/Sub channel로 발행합니다.
 */
@Component
@RequiredArgsConstructor
public class GameWaitingTimeoutPubSubPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final GameWaitingTimeoutPubSubMessageCodec messageCodec;

    public void publishTimeout(Long gameRoomId) {
        GameWaitingTimeoutPubSubMessage message = new GameWaitingTimeoutPubSubMessage(
                gameRoomId,
                GameWaitingConstants.WAITING_TIMEOUT_REASON,
                GameWaitingConstants.WAITING_TIMEOUT_ACTION
        );
        stringRedisTemplate.convertAndSend(
                GameWaitingConstants.WAITING_TIMEOUT_CHANNEL,
                messageCodec.encode(message)
        );
    }
}
