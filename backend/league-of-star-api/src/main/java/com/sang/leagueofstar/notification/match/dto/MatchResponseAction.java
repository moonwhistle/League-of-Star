package com.sang.leagueofstar.notification.match.dto;

/**
 * match_response_result 이벤트를 받은 클라이언트가 수행할 최종 화면 전환입니다.
 */
public enum MatchResponseAction {
    GO_TO_GAME_WAITING,
    GO_TO_MATCH_START,
    RETURN_TO_MATCHING
}
