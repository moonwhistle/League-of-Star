package com.sang.smite.game.end.common.constant;

public final class GameEndConstants {

    public static final String GAME_END_PENDING_KEY = "game:end:pending";
    public static final String ADVANCE_END_DEADLINE_LUA_SCRIPT_PATH =
            "scripts/game_end_advance_deadline_if_earlier.lua";
    public static final String UPDATE_END_DEADLINE_IF_DUE_LUA_SCRIPT_PATH =
            "scripts/game_end_update_deadline_if_due.lua";
    public static final String GAME_END_SCHEDULER_FIXED_DELAY_MS = "500";
    public static final int END_DEADLINE_CANDIDATE_BATCH_SIZE = 100;

    private GameEndConstants() {
    }
}
