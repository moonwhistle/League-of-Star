package com.sang.leagueofstar.game.end.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.service.GameEffectiveNaturalDeathService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameEndDeadlineAdvanceServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final long START_AT_MILLIS = 10_000L;

    private final GameEffectiveNaturalDeathService gameEffectiveNaturalDeathService =
            new GameEffectiveNaturalDeathService();
    private final GameEndScheduleService gameEndScheduleService = mock(GameEndScheduleService.class);
    private final GameEndDeadlineAdvanceService service = new GameEndDeadlineAdvanceService(
            gameEffectiveNaturalDeathService,
            gameEndScheduleService
    );

    @Test
    @DisplayName("advanceAfterFailedLightning - effective naturalDeathAt을 계산해 더 빠른 deadline 갱신을 요청한다")
    void advanceAfterFailedLightning() {
        // given
        GameRoom gameRoom = startedRoom();
        GameAction failedLightning = GameAction.lightning(
                GAME_ROOM_ID,
                FIRST_USER_ID,
                START_AT_MILLIS + 800L,
                800,
                3_000
        );

        // when
        service.advanceAfterFailedLightning(gameRoom, List.of(failedLightning));

        // then
        verify(gameEndScheduleService).advanceEndDeadlineIfEarlier(GAME_ROOM_ID, START_AT_MILLIS + 1_400L);
    }

    @Test
    @DisplayName("advanceAfterFailedLightning - deadline 갱신 실패가 SMITE 처리 흐름으로 전파되지 않는다")
    void advanceAfterFailedLightning_Exception() {
        // given
        GameRoom gameRoom = startedRoom();
        GameAction failedLightning = GameAction.lightning(
                GAME_ROOM_ID,
                FIRST_USER_ID,
                START_AT_MILLIS + 800L,
                800,
                3_000
        );
        doThrow(new RuntimeException("failed"))
                .when(gameEndScheduleService)
                .advanceEndDeadlineIfEarlier(GAME_ROOM_ID, START_AT_MILLIS + 1_400L);

        // when & then
        assertThatCode(() -> service.advanceAfterFailedLightning(gameRoom, List.of(failedLightning)))
                .doesNotThrowAnyException();
    }

    private GameRoom startedRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .durationSeconds(2)
                .scenarioData(GameScenario.of(List.of(
                        new HpStep(0, 10_000),
                        new HpStep(1_000, 2_000),
                        new HpStep(2_000, 0)
                )))
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.ofInstant(Instant.ofEpochMilli(START_AT_MILLIS), ZoneOffset.UTC));
        return gameRoom;
    }
}
