package com.sang.smite.common.path.match;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 관련 API 경로 정의 클래스입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchPath {
    public static final String MATCH_BASE = "/api/v1/match";
    public static final String JOIN = "/join";
    public static final String LEAVE = "/leave";
    public static final String ACCEPT = "/{matchId}/accept";
    public static final String REJECT = "/{matchId}/reject";
    public static final String MATCH_ID = "matchId";
}
