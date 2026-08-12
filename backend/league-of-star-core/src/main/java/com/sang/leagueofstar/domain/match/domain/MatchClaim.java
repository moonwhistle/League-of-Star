package com.sang.leagueofstar.domain.match.domain;

/**
 * FIFO 대기열에서 processing 영역으로 원자적으로 이동한 매칭 작업입니다.
 */
public record MatchClaim(
        String claimId,
        MatchTicket first,
        MatchTicket second
) {
}
