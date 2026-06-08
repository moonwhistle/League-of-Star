package com.sang.leagueofstar.matching.domain.event;

import com.sang.leagueofstar.domain.match.domain.MatchResponseStatus;
import com.sang.leagueofstar.domain.match.domain.MatchSession;
import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.matching.domain.result.GameSetupResult;

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
        MatchResponseStatus userBStatus,
        Game game
) {

    public MatchResponseResultEvent(
            String matchId,
            Long userA,
            Long userB,
            int userATierScore,
            int userBTierScore,
            MatchStatus sessionStatus,
            MatchResponseStatus userAStatus,
            MatchResponseStatus userBStatus
    ) {
        this(
                matchId,
                userA,
                userB,
                userATierScore,
                userBTierScore,
                sessionStatus,
                userAStatus,
                userBStatus,
                null
        );
    }

    public static MatchResponseResultEvent from(MatchSession session) {
        return new MatchResponseResultEvent(
                session.matchId(),
                session.userA(),
                session.userB(),
                session.userATierScore(),
                session.userBTierScore(),
                session.status(),
                session.userAStatus(),
                session.userBStatus(),
                null
        );
    }

    public static MatchResponseResultEvent from(MatchSession session, GameSetupResult gameSetupResult) {
        return new MatchResponseResultEvent(
                session.matchId(),
                session.userA(),
                session.userB(),
                session.userATierScore(),
                session.userBTierScore(),
                session.status(),
                session.userAStatus(),
                session.userBStatus(),
                new Game(
                        gameSetupResult.gameRoomId(),
                        gameSetupResult.videoUrl(),
                        gameSetupResult.webSocketUrl()
                )
        );
    }

    public record Game(
            Long gameRoomId,
            String videoUrl,
            String webSocketUrl
    ) {
    }
}
