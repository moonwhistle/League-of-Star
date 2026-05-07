package com.sang.smite.notification.pubsub.dto;

import com.sang.smite.domain.match.event.MatchFoundEvent;

import java.time.Instant;

/**
 * Redis Pub/Sub으로 API 인스턴스 전체에 전파할 매칭 성사 메시지입니다.
 *
 * <p>SSE payload는 대상 유저 기준으로 만들어지지만, Pub/Sub 메시지는 매칭 1건을 표현합니다.
 * 각 API 인스턴스는 이 메시지를 수신한 뒤 자기 메모리의 SSE 연결 저장소에서 userA/userB 연결을 찾아
 * 유저별 SSE payload로 변환해 전송합니다.</p>
 */
public record MatchFoundPubSubMessage(
        String matchId,
        Long userA,
        Long userB,
        int acceptTimeoutSeconds,
        Instant eventCreatedAt
) {

    public static MatchFoundPubSubMessage from(MatchFoundEvent event, Instant eventCreatedAt) {
        return new MatchFoundPubSubMessage(
                event.matchId(),
                event.userA(),
                event.userB(),
                event.acceptTimeoutSeconds(),
                eventCreatedAt
        );
    }
}
