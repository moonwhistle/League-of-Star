package com.sang.leagueofstar.game.rtt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameRttPingTrackerTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;

    private final GameRttPingTracker tracker = new GameRttPingTracker();

    @Test
    @DisplayName("recordSentAt - gameRoom/user/seq 기준으로 ping 전송 시각을 저장한다")
    void recordSentAt() {
        // when
        tracker.recordSentAt(GAME_ROOM_ID, USER_ID, 1, 10L);

        // then
        assertThat(tracker.findSentAt(GAME_ROOM_ID, USER_ID, 1)).hasValue(10L);
    }

    @Test
    @DisplayName("cleanup - gameRoom의 ping 전송 시각을 정리한다")
    void cleanup() {
        // given
        tracker.recordSentAt(GAME_ROOM_ID, USER_ID, 1, 10L);

        // when
        tracker.cleanup(GAME_ROOM_ID);

        // then
        assertThat(tracker.findSentAt(GAME_ROOM_ID, USER_ID, 1)).isEmpty();
    }

    @Test
    @DisplayName("consumeSentAt - ping 전송 시각을 한 번만 소비한다")
    void consumeSentAt() {
        // given
        tracker.recordSentAt(GAME_ROOM_ID, USER_ID, 1, 10L);

        // when & then
        assertThat(tracker.consumeSentAt(GAME_ROOM_ID, USER_ID, 1)).hasValue(10L);
        assertThat(tracker.consumeSentAt(GAME_ROOM_ID, USER_ID, 1)).isEmpty();
    }

    @Test
    @DisplayName("consumeTimedOutSentAts - timeout 기준을 지난 ping만 소비한다")
    void consumeTimedOutSentAts() {
        // given
        tracker.recordSentAt(GAME_ROOM_ID, USER_ID, 1, 10L);
        tracker.recordSentAt(GAME_ROOM_ID, USER_ID, 2, 90L);

        // when
        var timedOutPings = tracker.consumeTimedOutSentAts(120L, 50L);

        // then
        assertThat(timedOutPings).hasSize(1);
        assertThat(timedOutPings.get(0).seq()).isEqualTo(1);
        assertThat(tracker.findSentAt(GAME_ROOM_ID, USER_ID, 1)).isEmpty();
        assertThat(tracker.findSentAt(GAME_ROOM_ID, USER_ID, 2)).hasValue(90L);
    }
}
