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
        MatchResponseStatus userAStatus,
        MatchResponseStatus userBStatus
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
                MatchResponseStatus.PENDING,
                MatchResponseStatus.PENDING
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
        return userAStatus == MatchResponseStatus.ACCEPTED && userBStatus == MatchResponseStatus.ACCEPTED;
    }

    public boolean hasFailedResponse() {
        return userAStatus == MatchResponseStatus.REJECTED
                || userBStatus == MatchResponseStatus.REJECTED
                || userAStatus == MatchResponseStatus.TIMEOUT
                || userBStatus == MatchResponseStatus.TIMEOUT;
    }

    public boolean hasPendingResponse() {
        return userAStatus == MatchResponseStatus.PENDING || userBStatus == MatchResponseStatus.PENDING;
    }

    public boolean isRespondedByBoth() {
        return isRespondedBy(userA) && isRespondedBy(userB);
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
        return responseStatusOf(userId) == MatchResponseStatus.ACCEPTED;
    }

    public boolean rejectedBy(Long userId) {
        return responseStatusOf(userId) == MatchResponseStatus.REJECTED;
    }

    public boolean isRespondedBy(Long userId) {
        return responseStatusOf(userId).isResponded();
    }

    public MatchResponseStatus responseStatusOf(Long userId) {
        if (isUserA(userId)) {
            return userAStatus;
        }
        if (isUserB(userId)) {
            return userBStatus;
        }
        throw new IllegalArgumentException("매칭 세션 참여자가 아닙니다.");
    }

    public MatchSession accept(Long userId) {
        if (isUserA(userId)) {
            return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                    userBEntryTime, status, createdAt, MatchResponseStatus.ACCEPTED, userBStatus);
        }
        if (isUserB(userId)) {
            return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                    userBEntryTime, status, createdAt, userAStatus, MatchResponseStatus.ACCEPTED);
        }
        throw new IllegalArgumentException("매칭 세션 참여자가 아닙니다.");
    }

    public MatchSession reject(Long userId) {
        if (isUserA(userId)) {
            return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                    userBEntryTime, status, createdAt, MatchResponseStatus.REJECTED, userBStatus);
        }
        if (isUserB(userId)) {
            return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                    userBEntryTime, status, createdAt, userAStatus, MatchResponseStatus.REJECTED);
        }
        throw new IllegalArgumentException("매칭 세션 참여자가 아닙니다.");
    }

    public MatchSession timeoutPendingUsers() {
        MatchResponseStatus nextUserAStatus = userAStatus == MatchResponseStatus.PENDING
                ? MatchResponseStatus.TIMEOUT
                : userAStatus;
        MatchResponseStatus nextUserBStatus = userBStatus == MatchResponseStatus.PENDING
                ? MatchResponseStatus.TIMEOUT
                : userBStatus;
        return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                userBEntryTime, status, createdAt, nextUserAStatus, nextUserBStatus);
    }

    public MatchSession withStatus(MatchStatus status) {
        return new MatchSession(matchId, userA, userB, userATierScore, userBTierScore, userAEntryTime,
                userBEntryTime, status, createdAt, userAStatus, userBStatus);
    }
}
