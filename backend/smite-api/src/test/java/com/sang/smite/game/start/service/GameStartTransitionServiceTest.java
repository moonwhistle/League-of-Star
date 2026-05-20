package com.sang.smite.game.start.service;

import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.game.rtt.domain.GameRttStartReadyState;
import com.sang.smite.game.start.common.constant.GameStartConstants;
import com.sang.smite.game.start.domain.GameStartBlockedReason;
import com.sang.smite.game.start.domain.GameStartReadyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStartTransitionServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Instant SERVER_TIME = Instant.parse("2026-05-20T03:00:00Z");

    private final GameStartConditionService gameStartConditionService = mock(GameStartConditionService.class);
    private final GameRoomCommandService gameRoomCommandService = mock(GameRoomCommandService.class);
    private final Clock clock = Clock.fixed(SERVER_TIME, ZoneOffset.UTC);
    private final GameStartTransitionService service = new GameStartTransitionService(
            gameStartConditionService,
            gameRoomCommandService,
            clock
    );

    @Test
    @DisplayName("transitionToInProgress - 시작 조건을 통과하면 startAt을 확정하고 IN_PROGRESS 전환을 요청한다")
    void transitionToInProgress_Started() {
        // given
        Instant startAt = SERVER_TIME.plusMillis(GameStartConstants.START_DELAY_MILLIS);
        LocalDateTime startTime = LocalDateTime.ofInstant(startAt, ZoneOffset.UTC);
        when(gameStartConditionService.checkStartReady(GAME_ROOM_ID))
                .thenReturn(GameStartReadyResult.ready(new GameRttStartReadyState(
                        GAME_ROOM_ID,
                        1L,
                        2L,
                        30L,
                        40L
                )));
        when(gameRoomCommandService.startReadyRoomIfReady(GAME_ROOM_ID, startTime)).thenReturn(true);

        // when
        var result = service.transitionToInProgress(GAME_ROOM_ID);

        // then
        assertThat(result.started()).isTrue();
        assertThat(result.blockedReason()).isNull();
        assertThat(result.serverTimeMillis()).isEqualTo(SERVER_TIME.toEpochMilli());
        assertThat(result.startAtMillis()).isEqualTo(startAt.toEpochMilli());
        verify(gameRoomCommandService).startReadyRoomIfReady(GAME_ROOM_ID, startTime);
    }

    @Test
    @DisplayName("transitionToInProgress - 시작 조건을 통과하지 못하면 상태 전환하지 않는다")
    void transitionToInProgress_BlockedByCondition() {
        // given
        when(gameStartConditionService.checkStartReady(GAME_ROOM_ID))
                .thenReturn(GameStartReadyResult.blocked(GameStartBlockedReason.RTT_NOT_READY));

        // when
        var result = service.transitionToInProgress(GAME_ROOM_ID);

        // then
        assertThat(result.started()).isFalse();
        assertThat(result.blockedReason()).isEqualTo(GameStartBlockedReason.RTT_NOT_READY);
        verify(gameRoomCommandService, never()).startReadyRoomIfReady(
                anyLong(),
                any()
        );
    }

    @Test
    @DisplayName("transitionToInProgress - DB 상태 전환 실패 시 시작 실패로 반환한다")
    void transitionToInProgress_TransitionFailed() {
        // given
        Instant startAt = SERVER_TIME.plusMillis(GameStartConstants.START_DELAY_MILLIS);
        LocalDateTime startTime = LocalDateTime.ofInstant(startAt, ZoneOffset.UTC);
        when(gameStartConditionService.checkStartReady(GAME_ROOM_ID))
                .thenReturn(GameStartReadyResult.ready(new GameRttStartReadyState(
                        GAME_ROOM_ID,
                        1L,
                        2L,
                        30L,
                        40L
                )));
        when(gameRoomCommandService.startReadyRoomIfReady(GAME_ROOM_ID, startTime)).thenReturn(false);

        // when
        var result = service.transitionToInProgress(GAME_ROOM_ID);

        // then
        assertThat(result.started()).isFalse();
        assertThat(result.blockedReason()).isEqualTo(GameStartBlockedReason.GAME_ROOM_NOT_READY);
        assertThat(result.serverTimeMillis()).isZero();
        assertThat(result.startAtMillis()).isZero();
    }
}
