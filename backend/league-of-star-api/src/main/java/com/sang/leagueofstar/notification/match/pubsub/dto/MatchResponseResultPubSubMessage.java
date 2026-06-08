package com.sang.leagueofstar.notification.match.pubsub.dto;

import com.sang.leagueofstar.notification.match.dto.MatchResponseResultNotification;

/**
 * match_response_result SSE payload를 대상 유저별로 전달하는 Redis Pub/Sub 메시지입니다.
 */
public record MatchResponseResultPubSubMessage(
        Long targetUserId,
        MatchResponseResultNotification notification
) {
}
