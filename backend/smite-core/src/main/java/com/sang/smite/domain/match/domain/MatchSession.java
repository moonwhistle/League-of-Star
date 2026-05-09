package com.sang.smite.domain.match.domain;

/**
 * 매칭 성사 후 수락 대기 상태를 관리하는 세션 엔티티입니다.
 */
public record MatchSession(
        String matchId,
        Long userA,
        Long userB,
        int userATierScore,
        int userBTierScore,
        long userAEntryTime,
        long userBEntryTime,
        MatchStatus status,
        long createdAt,
        boolean userAAccepted,
        boolean userBAccepted
) {
    public static MatchSession create(
            String matchId,
            Long userA,
            Long userB,
            int userATierScore,
            int userBTierScore,
            long userAEntryTime,
            long userBEntryTime
    ) {
        return new MatchSession(
                matchId,
                userA,
                userB,
                userATierScore,
                userBTierScore,
                userAEntryTime,
                userBEntryTime,
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

    public int tierScoreOf(Long userId) {
        if (isUserA(userId)) {
            return userATierScore;
        }
        if (isUserB(userId)) {
            return userBTierScore;
        }
        throw new IllegalArgumentException("매칭 세션 참여자가 아닙니다.");
    }

    public long entryTimeOf(Long userId) {
        if (isUserA(userId)) {
            return userAEntryTime;
        }
        if (isUserB(userId)) {
            return userBEntryTime;
        }
        throw new IllegalArgumentException("매칭 세션 참여자가 아닙니다.");
    }

    public boolean acceptedBy(Long userId) {
        if (isUserA(userId)) {
            return userAAccepted;
        }
        if (isUserB(userId)) {
            return userBAccepted;
        }
        throw new IllegalArgumentException("매칭 세션 참여자가 아닙니다.");
    }

    public MatchSession accept(Long userId) {
        if (isUserA(userId)) {
            return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                    userBEntryTime, status, createdAt, true, userBAccepted);
        }
        if (isUserB(userId)) {
            return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                    userBEntryTime, status, createdAt, userAAccepted, true);
        }
        throw new IllegalArgumentException("매칭 세션 참여자가 아닙니다.");
    }

    public MatchSession withStatus(MatchStatus status) {
        return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                userBEntryTime, status, createdAt, userAAccepted, userBAccepted);
    }
}
