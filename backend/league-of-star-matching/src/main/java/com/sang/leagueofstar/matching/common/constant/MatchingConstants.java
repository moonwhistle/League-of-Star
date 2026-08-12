package com.sang.leagueofstar.matching.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 모듈 내에서 사용하는 공통 상수 클래스입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchingConstants {
    public static final String QUEUE_KEY = "matching:queue";
    public static final String MATCH_JOB_STREAM_KEY = "matching:jobs";
    public static final String MATCH_JOB_CONSUMER_GROUP = "matching-workers";
    public static final String STATUS_KEY_PREFIX = "match:status:";
    public static final String SESSION_KEY_PREFIX = "match:session:";
    public static final String TIMEOUT_PENDING_KEY = "match:response:timeout:pending";
    public static final String TIMEOUT_PROCESSING_KEY = "match:response:timeout:processing";
    public static final String ENQUEUE_MATCH_JOBS_LUA_SCRIPT_PATH = "scripts/enqueue_match_jobs.lua";
    public static final String COMPLETE_MATCH_JOBS_LUA_SCRIPT_PATH = "scripts/complete_match_jobs.lua";
    public static final String TIMEOUT_CLAIM_LUA_SCRIPT_PATH = "scripts/timeout_claim.lua";
    public static final String TIMEOUT_RECLAIM_LUA_SCRIPT_PATH = "scripts/timeout_reclaim.lua";
    public static final int MATCH_RESPONSE_TIMEOUT_SECONDS = 10;
    public static final String MATCH_ENGINE_FIXED_DELAY_MS = "50";
    public static final int MATCH_CLAIM_BATCH_SIZE = 100;
    public static final int MATCH_MAX_BATCHES_PER_SCAN = 100;
    public static final int MATCH_JOB_CONSUMER_BATCH_SIZE = 50;
    public static final long MATCH_JOB_CONSUMER_BLOCK_MILLIS = 1_000L;
    public static final long MATCH_JOB_MIN_IDLE_MILLIS = 5_000L;
    public static final String MATCH_CLAIM_RECOVERY_FIXED_DELAY_MS = "1000";
    public static final int MATCH_CLAIM_RECOVERY_BATCH_SIZE = 50;
    public static final String TIMEOUT_SCHEDULER_FIXED_DELAY_MS = "1000";
    public static final int TIMEOUT_CANDIDATE_BATCH_SIZE = 100;
    public static final long STATUS_TTL_SECONDS = 1800; // 30분
    public static final long MATCH_SESSION_TTL_SECONDS = 3600; // cleanup 실패 대비 안전장치 TTL 60분
    public static final long TIMEOUT_PROCESSING_LEASE_MILLIS = 5_000L;
}
