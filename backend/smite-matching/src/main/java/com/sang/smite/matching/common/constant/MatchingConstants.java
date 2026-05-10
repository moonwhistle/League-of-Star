package com.sang.smite.matching.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 모듈 내에서 사용하는 공통 상수 클래스입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchingConstants {
    public static final String QUEUE_KEY_PREFIX = "matching:queue:";
    public static final String STATUS_KEY_PREFIX = "match:status:";
    public static final String SESSION_KEY_PREFIX = "match:session:";
    public static final String LUA_SCRIPT_PATH = "scripts/atomic_pair_remove.lua";
    public static final int TIER_SCORE_MIN = 1;
    public static final int TIER_SCORE_MAX = 28;
    public static final long STATUS_TTL_SECONDS = 1800; // 30분
    public static final long MATCH_SESSION_TTL_SECONDS = 12; // 수락 제한 10초 + 네트워크/스케줄링 버퍼
}
