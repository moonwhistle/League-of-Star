package com.sang.leagueofstar.game.end.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.service.GameNaturalDeathSettlementService;
import com.sang.leagueofstar.domain.game.service.dto.GameNaturalDeathSettlementResult;
import com.sang.leagueofstar.game.end.common.constant.GameEndConstants;
import com.sang.leagueofstar.game.record.service.GameRecordRankSettlementTrigger;
import com.sang.leagueofstar.game.result.dto.GameResultPayload;
import com.sang.leagueofstar.game.result.service.GameResultPayloadFactory;
import com.sang.leagueofstar.game.result.service.GameResultWebSocketSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
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
    private final GameResultPayloadFactory gameResultPayloadFactory = new GameResultPayloadFactory();
    private final GameResultWebSocketSender gameResultWebSocketSender = mock(GameResultWebSocketSender.class);
    private final GameRecordRankSettlementTrigger gameRecordRankSettlementTrigger =
            mock(GameRecordRankSettlementTrigger.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GameEndSettlementService service = new GameEndSettlementService(
            gameEndScheduleService,
            gameNaturalDeathSettlementService,
            gameResultPayloadFactory,
            gameResultWebSocketSender,
            gameRecordRankSettlementTrigger,
            clock
    );

    @Test
    @DisplayName("processDueEndDeadlines - due gameRoom을 조회해 자연사 정산하고 완료 건은 cleanup한다")
    void processDueEndDeadlines_FinishedAndNoOp_Cleanup() throws Exception {
        // given
        when(gameEndScheduleService.findDueEndDeadlines(
                NOW.toEpochMilli(),
                GameEndConstants.END_DEADLINE_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(FIRST_GAME_ROOM_ID, SECOND_GAME_ROOM_ID));
        when(gameNaturalDeathSettlementService.settle(FIRST_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(finishedNaturalDeathResult(FIRST_GAME_ROOM_ID));
        when(gameNaturalDeathSettlementService.settle(SECOND_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(GameNaturalDeathSettlementResult.noOp());

        // when
        service.processDueEndDeadlines();

        // then
        ArgumentCaptor<GameResultPayload> payloadCaptor = ArgumentCaptor.forClass(GameResultPayload.class);
        verify(gameResultWebSocketSender).broadcastGameResult(
                org.mockito.ArgumentMatchers.eq(FIRST_GAME_ROOM_ID),
                payloadCaptor.capture()
        );
        GameResultPayload payload = payloadCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(payload.result()).isEqualTo(GameResult.DRAW);
        org.assertj.core.api.Assertions.assertThat(payload.winnerUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(payload.reason()).isEqualTo("NATURAL_DEATH_DRAW");
        org.assertj.core.api.Assertions.assertThat(payload.finishedAt()).isEqualTo(NOW.toEpochMilli());
        verify(gameRecordRankSettlementTrigger).settleFinishedGameRoomAfterCommit(
                org.mockito.ArgumentMatchers.argThat(gameRoom -> gameRoom.getId().equals(FIRST_GAME_ROOM_ID))
        );
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(
                org.mockito.ArgumentMatchers.argThat(gameRoom -> gameRoom.getId().equals(SECOND_GAME_ROOM_ID))
        );
        verify(gameEndScheduleService).cleanupEndDeadline(FIRST_GAME_ROOM_ID);
        verify(gameEndScheduleService).cleanupEndDeadline(SECOND_GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processDueEndDeadlines - effective HP가 남은 gameRoom은 더 늦은 naturalDeathAt으로 갱신한다")
    void processDueEndDeadlines_Rescheduled_UpdateDeadline() throws Exception {
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
        verify(gameResultWebSocketSender, never()).broadcastGameResult(
                org.mockito.ArgumentMatchers.eq(FIRST_GAME_ROOM_ID),
                org.mockito.ArgumentMatchers.any(GameResultPayload.class)
        );
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(
                org.mockito.ArgumentMatchers.any(GameRoom.class)
        );
        verify(gameEndScheduleService, never()).cleanupEndDeadline(FIRST_GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processDueEndDeadlines - 특정 gameRoom 정산이 실패해도 다음 gameRoom 처리를 계속한다")
    void processDueEndDeadlines_Exception_ContinueNext() throws Exception {
        // given
        when(gameEndScheduleService.findDueEndDeadlines(
                NOW.toEpochMilli(),
                GameEndConstants.END_DEADLINE_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(FIRST_GAME_ROOM_ID, SECOND_GAME_ROOM_ID));
        doThrow(new RuntimeException("failed"))
                .when(gameNaturalDeathSettlementService)
                .settle(FIRST_GAME_ROOM_ID, NOW.toEpochMilli());
        when(gameNaturalDeathSettlementService.settle(SECOND_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(finishedNaturalDeathResult(SECOND_GAME_ROOM_ID));

        // when
        service.processDueEndDeadlines();

        // then
        verify(gameNaturalDeathSettlementService).settle(SECOND_GAME_ROOM_ID, NOW.toEpochMilli());
        verify(gameResultWebSocketSender).broadcastGameResult(
                org.mockito.ArgumentMatchers.eq(SECOND_GAME_ROOM_ID),
                org.mockito.ArgumentMatchers.any(GameResultPayload.class)
        );
        verify(gameRecordRankSettlementTrigger).settleFinishedGameRoomAfterCommit(
                org.mockito.ArgumentMatchers.argThat(gameRoom -> gameRoom.getId().equals(SECOND_GAME_ROOM_ID))
        );
        verify(gameEndScheduleService).cleanupEndDeadline(SECOND_GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processDueEndDeadlines - GAME_RESULT broadcast가 실패해도 pending cleanup을 시도한다")
    void processDueEndDeadlines_GameResultBroadcastFailed_Cleanup() throws Exception {
        // given
        when(gameEndScheduleService.findDueEndDeadlines(
                NOW.toEpochMilli(),
                GameEndConstants.END_DEADLINE_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(FIRST_GAME_ROOM_ID));
        when(gameNaturalDeathSettlementService.settle(FIRST_GAME_ROOM_ID, NOW.toEpochMilli()))
                .thenReturn(finishedNaturalDeathResult(FIRST_GAME_ROOM_ID));
        doThrow(new IOException("failed"))
                .when(gameResultWebSocketSender)
                .broadcastGameResult(
                        org.mockito.ArgumentMatchers.eq(FIRST_GAME_ROOM_ID),
                        org.mockito.ArgumentMatchers.any(GameResultPayload.class)
                );

        // when
        service.processDueEndDeadlines();

        // then
        verify(gameRecordRankSettlementTrigger).settleFinishedGameRoomAfterCommit(
                org.mockito.ArgumentMatchers.argThat(gameRoom -> gameRoom.getId().equals(FIRST_GAME_ROOM_ID))
        );
        verify(gameEndScheduleService).cleanupEndDeadline(FIRST_GAME_ROOM_ID);
    }

    private GameNaturalDeathSettlementResult finishedNaturalDeathResult(Long gameRoomId) {
        GameRoom gameRoom = GameRoom.builder()
                .id(gameRoomId)
                .build();
        gameRoom.finish(GameResult.DRAW, null);
        GameAction action = GameAction.smite(gameRoomId, 1L, NOW.toEpochMilli() - 100L, 900, 1_000);
        return GameNaturalDeathSettlementResult.finished(gameRoom, List.of(action));
    }
}
