package com.sang.smite.game.end.service;

import com.sang.smite.domain.game.service.GameNaturalDeathSettlementResult;
import com.sang.smite.domain.game.service.GameNaturalDeathSettlementService;
import com.sang.smite.game.end.common.constant.GameEndConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameEndSettlementServiceTest {

    private static final Long FIRST_GAME_ROOM_ID = 100L;
    private static final Long SECOND_GAME_ROOM_ID = 101L;
    private static final Instant NOW = Instant.parse("2026-05-22T03:00:00Z");

    private final GameEndScheduleService gameEndScheduleService = mock(GameEndScheduleService.class);
    private final GameNaturalDeathSettlementService gameNaturalDeathSettlementService =
            mock(GameNaturalDeathSettlementService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GameEndSettlementService service = new GameEndSettlementService(
            gameEndScheduleService,
            gameNaturalDeathSettlementService,
            clock
    );

    @Test
    @DisplayName("processDueEndDeadlines - due gameRoom을 조회해 자연사 정산하고 완료 건은 cleanup한다")
    void processDueEndDeadlines_FinishedAndNoOp_Cleanup() {
        // given
        when(gameEndScheduleService.findDueEndDeadlines(
                NOW.toEpochMilli(),
                GameEndConstants.END_DEADLINE_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(FIRST_GAME_ROOM_ID, SECOND_GAME_ROOM_ID));
        when(gameNaturalDeathSettlementService.settle(FIRST_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(GameNaturalDeathSettlementResult.finished());
        when(gameNaturalDeathSettlementService.settle(SECOND_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(GameNaturalDeathSettlementResult.noOp());

        // when
        service.processDueEndDeadlines();

        // then
        verify(gameEndScheduleService).cleanupEndDeadline(FIRST_GAME_ROOM_ID);
        verify(gameEndScheduleService).cleanupEndDeadline(SECOND_GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processDueEndDeadlines - effective HP가 남은 gameRoom은 더 늦은 naturalDeathAt으로 갱신한다")
    void processDueEndDeadlines_Rescheduled_UpdateDeadline() {
        // given
        long nextNaturalDeathAtMillis = NOW.toEpochMilli() + 500L;
        when(gameEndScheduleService.findDueEndDeadlines(
                NOW.toEpochMilli(),
                GameEndConstants.END_DEADLINE_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(FIRST_GAME_ROOM_ID));
        when(gameNaturalDeathSettlementService.settle(FIRST_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(GameNaturalDeathSettlementResult.rescheduled(nextNaturalDeathAtMillis));

        // when
        service.processDueEndDeadlines();

        // then
        verify(gameEndScheduleService).updateEndDeadlineIfDue(
                FIRST_GAME_ROOM_ID,
                NOW.toEpochMilli(),
                nextNaturalDeathAtMillis
        );
        verify(gameEndScheduleService, never()).cleanupEndDeadline(FIRST_GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processDueEndDeadlines - 특정 gameRoom 정산이 실패해도 다음 gameRoom 처리를 계속한다")
    void processDueEndDeadlines_Exception_ContinueNext() {
        // given
        when(gameEndScheduleService.findDueEndDeadlines(
                NOW.toEpochMilli(),
                GameEndConstants.END_DEADLINE_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(FIRST_GAME_ROOM_ID, SECOND_GAME_ROOM_ID));
        doThrow(new RuntimeException("failed"))
                .when(gameNaturalDeathSettlementService)
                .settle(FIRST_GAME_ROOM_ID, NOW.toEpochMilli());
        when(gameNaturalDeathSettlementService.settle(SECOND_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(GameNaturalDeathSettlementResult.finished());

        // when
        service.processDueEndDeadlines();

        // then
        verify(gameNaturalDeathSettlementService).settle(SECOND_GAME_ROOM_ID, NOW.toEpochMilli());
        verify(gameEndScheduleService).cleanupEndDeadline(SECOND_GAME_ROOM_ID);
    }
}
