package com.sang.smite.matching.domain.result;

/**
 * timeout 정산 후 service layer 안에서 전달하는 내부 처리 결과입니다.
 */
public record MatchResponseTimeoutResult(
        boolean settled,
        int returnedUserCount
) {

    public static MatchResponseTimeoutResult settled(int returnedUserCount) {
        return new MatchResponseTimeoutResult(true, returnedUserCount);
    }

    public static MatchResponseTimeoutResult noOp() {
        return new MatchResponseTimeoutResult(false, 0);
    }
}
