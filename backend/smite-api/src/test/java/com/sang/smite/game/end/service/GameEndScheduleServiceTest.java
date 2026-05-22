package com.sang.smite.game.end.service;

import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;
import com.sang.smite.game.end.repository.GameEndScheduleStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameEndScheduleServiceTest {

    private static final Long GAME_ROOM_ID = 100L;

    private final GameEndScheduleStore gameEndScheduleStore = mock(GameEndScheduleStore.class);
    private final GameEndScheduleService service = new GameEndScheduleService(gameEndScheduleStore);

    @Test
    @DisplayName("registerEndDeadline - scenario duration 기준 naturalDeathAt을 등록한다")
    void registerEndDeadline() {
        // given
        long startAtMillis = 1_000L;
        long durationMs = 8_000L;

        // when
        GameEndDeadlineRegistration result = service.registerEndDeadline(GAME_ROOM_ID, startAtMillis, durationMs);

        // then
        assertThat(result.gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.naturalDeathAtMillis()).isEqualTo(9_000L);
        verify(gameEndScheduleStore).registerEndDeadline(result);
    }

    @Test
    @DisplayName("advanceEndDeadlineIfEarlier - 더 빠른 naturalDeathAt 갱신을 저장소에 위임한다")
    void advanceEndDeadlineIfEarlier() {
        // when
        service.advanceEndDeadlineIfEarlier(GAME_ROOM_ID, 8_000L);

        // then
        verify(gameEndScheduleStore).advanceEndDeadlineIfEarlier(GAME_ROOM_ID, 8_000L);
    }

    @Test
    @DisplayName("updateEndDeadlineIfDue - due 상태 deadline 갱신을 저장소에 위임한다")
    void updateEndDeadlineIfDue() {
        // when
        service.updateEndDeadlineIfDue(GAME_ROOM_ID, 10_000L, 12_000L);

        // then
        verify(gameEndScheduleStore).updateEndDeadlineIfDue(GAME_ROOM_ID, 10_000L, 12_000L);
    }

    @Test
    @DisplayName("findDueEndDeadlines - naturalDeathAt이 지난 gameRoomId 조회를 저장소에 위임한다")
    void findDueEndDeadlines() {
        // given
        when(gameEndScheduleStore.findDueEndDeadlines(10_000L, 100))
                .thenReturn(List.of(100L, 101L));

        // when
        List<Long> result = service.findDueEndDeadlines(10_000L, 100);

        // then
        assertThat(result).containsExactly(100L, 101L);
        verify(gameEndScheduleStore).findDueEndDeadlines(10_000L, 100);
    }

    @Test
    @DisplayName("cleanupEndDeadline - 등록된 종료 정산 deadline 제거를 저장소에 위임한다")
    void cleanupEndDeadline() {
        // when
        service.cleanupEndDeadline(GAME_ROOM_ID);

        // then
        verify(gameEndScheduleStore).cleanupEndDeadline(GAME_ROOM_ID);
    }
}
