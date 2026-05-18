package com.sang.smite.game.waiting.pubsub.dto;

/**
 * game waiting timeout Pub/Sub 메시지입니다.
 */
public record GameWaitingTimeoutPubSubMessage(
        Long gameRoomId,
        String reason,
        String action
) {
}
