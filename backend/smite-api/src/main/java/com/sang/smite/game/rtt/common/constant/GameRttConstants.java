package com.sang.smite.game.rtt.common.constant;

public final class GameRttConstants {

    public static final String RTT_KEY_PREFIX = "game:rtt:";

    public static final String USER_A_ID_FIELD = "userAId";
    public static final String USER_B_ID_FIELD = "userBId";
    public static final String USER_A_SAMPLES_FIELD = "userASamples";
    public static final String USER_B_SAMPLES_FIELD = "userBSamples";
    public static final String USER_A_MEDIAN_RTT_MS_FIELD = "userAMedianRttMs";
    public static final String USER_B_MEDIAN_RTT_MS_FIELD = "userBMedianRttMs";
    public static final String USER_A_STATUS_FIELD = "userAStatus";
    public static final String USER_B_STATUS_FIELD = "userBStatus";

    public static final int REQUIRED_RTT_SAMPLE_COUNT = 5;
    public static final int INITIAL_RTT_SEQUENCE = 1;
    public static final int RTT_LIMIT_MILLIS = 2_000;
    public static final int RTT_PING_TIMEOUT_MILLIS = 2_500;
    public static final int RTT_MEASUREMENT_TIMEOUT_SECONDS = 15;
    public static final int RTT_STATE_TTL_SECONDS = 300;

    private GameRttConstants() {
    }
}
