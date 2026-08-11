package com.sang.leagueofstar.domain.match.domain;

/**
 * FIFO 대기열에서 원자적으로 확보한 두 사용자 티켓입니다.
 */
public record MatchPair(
        MatchTicket first,
        MatchTicket second
) {
}
