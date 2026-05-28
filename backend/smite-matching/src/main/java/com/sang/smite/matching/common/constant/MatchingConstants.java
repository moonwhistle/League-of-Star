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
    public static final String TIMEOUT_PENDING_KEY = "match:response:timeout:pending";
    public static final String TIMEOUT_PROCESSING_KEY = "match:response:timeout:processing";
    public static final String LUA_SCRIPT_PATH = "scripts/atomic_pair_remove.lua";
    public static final String TIMEOUT_CLAIM_LUA_SCRIPT_PATH = "scripts/timeout_claim.lua";
    public static final String TIMEOUT_RECLAIM_LUA_SCRIPT_PATH = "scripts/timeout_reclaim.lua";
    public static final int TIER_SCORE_MIN = 1;
    public static final int TIER_SCORE_MAX = 37;
    public static final int MATCH_RESPONSE_TIMEOUT_SECONDS = 10;
    public static final String TIMEOUT_SCHEDULER_FIXED_DELAY_MS = "1000";
    public static final int TIMEOUT_CANDIDATE_BATCH_SIZE = 100;
    public static final long STATUS_TTL_SECONDS = 1800; // 30분
    public static final long MATCH_SESSION_TTL_SECONDS = 3600; // cleanup 실패 대비 안전장치 TTL 60분
    public static final long TIMEOUT_PROCESSING_LEASE_MILLIS = 5_000L;
}
