package com.sang.leagueofstar.game.practice.service;

import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.game.end.service.GameEndScheduleService;
import com.sang.leagueofstar.game.start.common.constant.GameStartConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamePracticeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GAME_ROOM_ID = 100L;
    private static final Instant SERVER_TIME = Instant.parse("2026-06-22T12:00:00Z");

    private final GameRoomReadService gameRoomReadService = mock(GameRoomReadService.class);
    private final GameRoomCommandService gameRoomCommandService = mock(GameRoomCommandService.class);
    private final GameEndScheduleService gameEndScheduleService = mock(GameEndScheduleService.class);
    private final Clock clock = Clock.fixed(SERVER_TIME, ZoneOffset.UTC);
    private final GamePracticeService gamePracticeService = new GamePracticeService(
            gameRoomReadService,
            gameRoomCommandService,
            gameEndScheduleService,
            clock
    );

    @Test
    @DisplayName("startPractice - active room이 없으면 practice room을 생성하고 시작 정보를 반환한다")
    void startPractice_Success() {
        // given
        Instant startAt = SERVER_TIME.plusMillis(GameStartConstants.START_DELAY_MILLIS);
        GameRoom gameRoom = practiceRoom();
        when(gameRoomReadService.existsActiveGameRoomByUserId(USER_ID)).thenReturn(false);
        when(gameRoomCommandService.createPracticeRoom(USER_ID)).thenReturn(gameRoom);
        when(gameRoomCommandService.startReadyRoomIfReady(
                GAME_ROOM_ID,
                LocalDateTime.ofInstant(startAt, ZoneOffset.UTC)
        )).thenReturn(true);

        // when
        var result = gamePracticeService.startPractice(USER_ID);

        // then
        assertThat(result.gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.serverTime()).isEqualTo(SERVER_TIME.toEpochMilli());
        assertThat(result.startAt()).isEqualTo(startAt.toEpochMilli());
        assertThat(result.webSocketUrl()).isEqualTo("/ws/game/100");
        assertThat(result.scenario().starCoreMaxHp()).isEqualTo(10_000);
        assertThat(result.scenario().durationMs()).isEqualTo(12_000L);
        verify(gameEndScheduleService).registerEndDeadline(GAME_ROOM_ID, startAt.toEpochMilli(), 12_000L);
    }

    @Test
    @DisplayName("startPractice - active room이 있으면 practice room을 생성하지 않는다")
    void startPractice_ActiveGameRoom_ThrowException() {
        // given
        when(gameRoomReadService.existsActiveGameRoomByUserId(USER_ID)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> gamePracticeService.startPractice(USER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.GAME_ACTIVE_ROOM_EXISTS));
        verify(gameRoomCommandService, never()).createPracticeRoom(USER_ID);
    }

    @Test
    @DisplayName("startPractice - IN_PROGRESS 전환 실패 시 READY room abort 후 예외를 던진다")
    void startPractice_StartFailed_AbortReadyRoom() {
        // given
        Instant startAt = SERVER_TIME.plusMillis(GameStartConstants.START_DELAY_MILLIS);
        GameRoom gameRoom = practiceRoom();
        when(gameRoomReadService.existsActiveGameRoomByUserId(USER_ID)).thenReturn(false);
        when(gameRoomCommandService.createPracticeRoom(USER_ID)).thenReturn(gameRoom);
        when(gameRoomCommandService.startReadyRoomIfReady(
                GAME_ROOM_ID,
                LocalDateTime.ofInstant(startAt, ZoneOffset.UTC)
        )).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> gamePracticeService.startPractice(USER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.GAME_PRACTICE_START_FAILED));
        verify(gameRoomCommandService).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(gameEndScheduleService, never()).registerEndDeadline(GAME_ROOM_ID, startAt.toEpochMilli(), 12_000L);
    }

    @Test
    @DisplayName("startPractice - end deadline 등록 실패 시 IN_PROGRESS room abort 후 예외를 전파한다")
    void startPractice_EndDeadlineRegistrationFailed_AbortInProgressRoom() {
        // given
        Instant startAt = SERVER_TIME.plusMillis(GameStartConstants.START_DELAY_MILLIS);
        RuntimeException failure = new RuntimeException("redis failed");
        GameRoom gameRoom = practiceRoom();
        when(gameRoomReadService.existsActiveGameRoomByUserId(USER_ID)).thenReturn(false);
        when(gameRoomCommandService.createPracticeRoom(USER_ID)).thenReturn(gameRoom);
        when(gameRoomCommandService.startReadyRoomIfReady(
                GAME_ROOM_ID,
                LocalDateTime.ofInstant(startAt, ZoneOffset.UTC)
        )).thenReturn(true);
        org.mockito.Mockito.doThrow(failure)
                .when(gameEndScheduleService)
                .registerEndDeadline(GAME_ROOM_ID, startAt.toEpochMilli(), 12_000L);

        // when & then
        assertThatThrownBy(() -> gamePracticeService.startPractice(USER_ID)).isSameAs(failure);
        verify(gameRoomCommandService).abortInProgressRoomIfInProgress(GAME_ROOM_ID);
    }

    private GameRoom practiceRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .gameMode(GameMode.PRACTICE)
                .scenarioData(GameScenario.of(List.of(
                        new HpStep(0L, 10_000),
                        new HpStep(12_000L, 0)
                )))
                .build();
        gameRoom.addParticipant(USER_ID);
        return gameRoom;
    }
}
