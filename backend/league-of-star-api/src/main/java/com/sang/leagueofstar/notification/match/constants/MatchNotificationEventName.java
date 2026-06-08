package com.sang.leagueofstar.notification.match.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchNotificationEventName {

    public static final String CONNECTED = "connected";
    public static final String HEARTBEAT = "heartbeat";
    public static final String MATCH_FOUND = "match_found";
    public static final String MATCH_RESPONSE_RESULT = "match_response_result";
}
