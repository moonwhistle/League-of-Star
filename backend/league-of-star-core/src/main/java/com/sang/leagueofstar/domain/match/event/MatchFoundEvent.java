package com.sang.leagueofstar.domain.match.event;

/**
 * 매칭이 성사되었을 때 발행되는 이벤트입니다.
 * 이 이벤트를 구독하여 클라이언트에게 match_found SSE 알림을 전송할 수 있습니다.
 */
public record MatchFoundEvent(
        String matchId,
        Long userA,
        Long userB,
        int acceptTimeoutSeconds
) {}
