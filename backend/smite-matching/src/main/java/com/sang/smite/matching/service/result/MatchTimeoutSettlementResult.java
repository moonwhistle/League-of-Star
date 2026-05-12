package com.sang.smite.matching.service.result;

/**
 * timeout 정산 후 service layer 안에서 전달하는 내부 처리 결과입니다.
 */
public record MatchTimeoutSettlementResult(
        boolean settled,
        int returnedUserCount
) {

    public static MatchTimeoutSettlementResult settled(int returnedUserCount) {
        return new MatchTimeoutSettlementResult(true, returnedUserCount);
    }

    public static MatchTimeoutSettlementResult noOp() {
        return new MatchTimeoutSettlementResult(false, 0);
    }
}
