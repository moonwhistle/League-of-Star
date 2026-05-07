package com.sang.smite.domain.match.domain;

/**
 * 매칭 성사 후 수락 대기 상태를 관리하는 세션 엔티티입니다.
 */
public record MatchSession(
        String matchId,
        Long userA,
        Long userB,
        MatchStatus status,
        long createdAt,
        boolean userAAccepted,
        boolean userBAccepted
) {
    public static MatchSession create(String matchId, Long userA, Long userB) {
        return new MatchSession(
                matchId,
                userA,
                userB,
                MatchStatus.FOUND,
                System.currentTimeMillis(),
                false,
                false
        );
    }

    public boolean isParticipant(Long userId) {
        return isUserA(userId) || isUserB(userId);
    }

    public boolean isUserA(Long userId) {
        return userA.equals(userId);
    }

    public boolean isUserB(Long userId) {
        return userB.equals(userId);
    }

    public boolean isAcceptedByBoth() {
        return userAAccepted && userBAccepted;
    }
}
