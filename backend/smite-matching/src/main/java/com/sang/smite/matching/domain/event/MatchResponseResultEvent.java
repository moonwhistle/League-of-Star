package com.sang.smite.matching.domain.event;

import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;

/**
 * matchId 단위 매칭 응답이 최종 정산된 뒤 발행되는 matching 내부 이벤트입니다.
 */
public record MatchResponseResultEvent(
        String matchId,
        Long userA,
        Long userB,
        int userATierScore,
        int userBTierScore,
        MatchStatus sessionStatus,
        MatchResponseStatus userAStatus,
        MatchResponseStatus userBStatus
) {

    public static MatchResponseResultEvent from(MatchSession session) {
        return new MatchResponseResultEvent(
                session.matchId(),
                session.userA(),
                session.userB(),
                session.userATierScore(),
                session.userBTierScore(),
                session.status(),
                session.userAStatus(),
                session.userBStatus()
        );
    }
}
