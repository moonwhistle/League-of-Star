package com.sang.smite.notification.match.dto;

/**
 * match_response_result 이벤트가 발생한 최종 정산 사유입니다.
 */
public enum MatchResponseReason {
    BOTH_ACCEPTED,
    MY_REJECTED,
    OPPONENT_REJECTED,
    MY_TIMEOUT,
    OPPONENT_TIMEOUT,
    BOTH_TIMEOUT,
    GAME_SETUP_FAILED
}
