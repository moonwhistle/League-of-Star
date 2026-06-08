package com.sang.leagueofstar.notification.match.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 알림 Redis Pub/Sub channel 이름을 정의합니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchNotificationChannelName {

    public static final String MATCH_FOUND = "notification:match_found";
    public static final String MATCH_RESPONSE_RESULT = "notification:match_response_result";
}
