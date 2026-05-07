package com.sang.smite.common.path.notification;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 알림 관련 API 경로 정의 클래스입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotificationPath {

    public static final String NOTIFICATION_BASE = "/api/v1/notifications";
    public static final String MATCH_STREAM = "/match/stream";
}
