package com.sang.smite.redis.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 분산 락 관련 공통 상수 클래스입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LockConstants {
    public static final String REDISSON_LOCK_PREFIX = "LOCK:";
}
