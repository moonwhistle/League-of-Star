package com.sang.smite.game.waiting.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 게임 대기 WebSocket timeout 흐름에서 사용하는 Redis key와 정책 상수입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GameWaitingConstants {

    public static final String WAITING_TIMEOUT_PENDING_KEY = "game:waiting:timeout:pending";
    public static final String WAITING_KEY_PREFIX = "game:waiting:";
    public static final String WAITING_TIMEOUT_LOCK_KEY_PREFIX = "game:waiting:timeout:lock:";
    public static final String WAITING_TIMEOUT_CHANNEL = "game_waiting_timeout";
    public static final String WAITING_TIMEOUT_REASON = "WAITING_TIMEOUT";
    public static final String WAITING_TIMEOUT_ACTION = "GO_TO_MATCH_START";

    public static final String USER_A_ID_FIELD = "userAId";
    public static final String USER_B_ID_FIELD = "userBId";
    public static final String USER_A_READY_FIELD = "userAReady";
    public static final String USER_B_READY_FIELD = "userBReady";
    public static final String CREATED_AT_MILLIS_FIELD = "createdAtMillis";
    public static final String DEADLINE_AT_MILLIS_FIELD = "deadlineAtMillis";

    public static final long WAITING_TIMEOUT_SECONDS = 30L;
    public static final long WAITING_STATE_TTL_SECONDS = 60L;
    public static final String WAITING_TIMEOUT_SCHEDULER_FIXED_DELAY_MS = "1000";
    public static final int WAITING_TIMEOUT_CANDIDATE_BATCH_SIZE = 100;
}
