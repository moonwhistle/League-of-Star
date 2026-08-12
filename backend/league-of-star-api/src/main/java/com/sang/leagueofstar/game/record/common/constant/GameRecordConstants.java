package com.sang.leagueofstar.game.record.common.constant;

public final class GameRecordConstants {

    public static final long SETTLED_RECORD_COUNT = 2L;
    public static final String OUTBOX_POLLING_FIXED_DELAY_MS = "500";
    public static final int OUTBOX_PROCESS_BATCH_SIZE = 100;
    public static final int OUTBOX_CLAIM_CANDIDATE_MULTIPLIER = 2;
    public static final long OUTBOX_LEASE_SECONDS = 30L;
    public static final long OUTBOX_MAX_RETRY_DELAY_SECONDS = 60L;
    public static final int OUTBOX_LAST_ERROR_MAX_LENGTH = 1000;

    private GameRecordConstants() {
    }
}
