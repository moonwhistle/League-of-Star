package com.sang.leagueofstar.domain.match.domain;

/**
 * 매칭 대기열에 진입한 유저의 정보를 담는 가치 객체(Value Object)입니다.
 */
public record MatchTicket(
        Long userId,
        long entryTime
) {
}
