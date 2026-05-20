package com.sang.smite.game.end.service;

import com.sang.smite.game.end.common.constant.GameEndConstants;
import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;
import com.sang.smite.game.end.repository.GameEndScheduleStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameEndScheduleServiceTest {

    private static final Long GAME_ROOM_ID = 100L;

    private final GameEndScheduleStore gameEndScheduleStore = mock(GameEndScheduleStore.class);
    private final GameEndScheduleService service = new GameEndScheduleService(gameEndScheduleStore);

    @Test
    @DisplayName("registerEndDeadline - scenario duration과 입력 유예 시간을 기준으로 종료 정산 deadline을 등록한다")
    void registerEndDeadline() {
        // given
        long startAtMillis = 1_000L;
        long durationMs = 8_000L;

        // when
        GameEndDeadlineRegistration result = service.registerEndDeadline(GAME_ROOM_ID, startAtMillis, durationMs);

        // then
        assertThat(result.gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.gameEndAtMillis()).isEqualTo(9_000L);
        assertThat(result.settlementDueAtMillis()).isEqualTo(9_000L + GameEndConstants.INPUT_GRACE_MILLIS);
        verify(gameEndScheduleStore).registerEndDeadline(result);
    }
}
