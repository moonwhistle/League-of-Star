package com.sang.smite.domain.match.domain;

/**
 * 매칭 대기열에 진입한 유저의 정보를 담는 가치 객체(Value Object)입니다.
 */
public record MatchTicket(
        Long userId,
        int tierScore,
        long entryTime
) {
    public static MatchTicket of(Long userId, int tierScore) {
        return new MatchTicket(userId, tierScore, System.currentTimeMillis());
    }

    /**
     * 특정 시점 기준 대기 시간을 초 단위로 계산합니다.
     */
    public long getWaitTimeSeconds(long now) {
        return (now - entryTime) / 1000;
    }
}
