package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameParticipant;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.domain.vo.ParticipantStatus;
import com.sang.leagueofstar.domain.game.repository.GameActionRepository;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.dto.GameNaturalDeathSettlementResult;
import com.sang.leagueofstar.domain.game.service.dto.GameNaturalDeathSettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameNaturalDeathSettlementServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final long START_AT_MILLIS = 10_000L;

    private final GameRoomRepository gameRoomRepository = mock(GameRoomRepository.class);
    private final GameActionRepository gameActionRepository = mock(GameActionRepository.class);
    private final GameEffectiveNaturalDeathService gameEffectiveNaturalDeathService =
            new GameEffectiveNaturalDeathService();
    private final GameRoomCommandService gameRoomCommandService = new GameRoomCommandService(
            gameRoomRepository,
            mock(GameScenarioGenerator.class)
    );
    private final GameNaturalDeathSettlementService service = new GameNaturalDeathSettlementService(
            gameRoomRepository,
            gameActionRepository,
            gameEffectiveNaturalDeathService,
            gameRoomCommandService
    );

    @Test
    @DisplayName("settle - IN_PROGRESS gameRoom의 effective HP가 0이면 자연사 DRAW로 종료한다")
    void settle_InProgressNaturalDeath_FinishDraw() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 0)
        ));
        when(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).thenReturn(Optional.of(gameRoom));
        when(gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of());

        // when
        GameNaturalDeathSettlementResult result = service.settle(GAME_ROOM_ID, START_AT_MILLIS + 1_000L);

        // then
        assertThat(result.status()).isEqualTo(GameNaturalDeathSettlementStatus.FINISHED);
        assertThat(result.finishedGameRoom()).isSameAs(gameRoom);
        assertThat(result.actions()).isEmpty();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(gameRoom.getResult()).isEqualTo(GameResult.DRAW);
        assertThat(gameRoom.getWinnerId()).isNull();
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.FINISHED);
    }

    @Test
    @DisplayName("settle - due 조회됐지만 effective HP가 남아 있으면 다음 naturalDeathAt을 반환한다")
    void settle_EffectiveHpRemaining_Reschedule() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 2_000),
                new HpStep(2_000, 0)
        ));
        GameAction failedSmite = GameAction.smite(
                GAME_ROOM_ID,
                FIRST_USER_ID,
                START_AT_MILLIS + 800L,
                800,
                3_000
        );
        when(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).thenReturn(Optional.of(gameRoom));
        when(gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(failedSmite));

        // when
        GameNaturalDeathSettlementResult result = service.settle(GAME_ROOM_ID, START_AT_MILLIS + 1_000L);

        // then
        assertThat(result.status()).isEqualTo(GameNaturalDeathSettlementStatus.RESCHEDULED);
        assertThat(result.nextNaturalDeathAtMillis()).isEqualTo(START_AT_MILLIS + 1_400L);
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("settle - IN_PROGRESS가 아니면 상태를 바꾸지 않고 no-op 처리한다")
    void settle_NotInProgress_NoOp() {
        // given
        GameRoom gameRoom = readyRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 0)
        ));
        when(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).thenReturn(Optional.of(gameRoom));

        // when
        GameNaturalDeathSettlementResult result = service.settle(GAME_ROOM_ID, START_AT_MILLIS + 1_000L);

        // then
        assertThat(result.status()).isEqualTo(GameNaturalDeathSettlementStatus.NO_OP);
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.READY);
        verify(gameActionRepository, never()).findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("settle - ABORTED이면 상태를 바꾸지 않고 no-op 처리한다")
    void settle_Aborted_NoOp() {
        // given
        GameRoom gameRoom = readyRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 0)
        ));
        gameRoom.abortBeforeStart();
        when(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).thenReturn(Optional.of(gameRoom));

        // when
        GameNaturalDeathSettlementResult result = service.settle(GAME_ROOM_ID, START_AT_MILLIS + 1_000L);

        // then
        assertThat(result.status()).isEqualTo(GameNaturalDeathSettlementStatus.NO_OP);
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(gameRoom.getResult()).isNull();
        verify(gameActionRepository, never()).findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("settle - 같은 gameRoom을 두 번 정산해도 첫 결과를 유지한다")
    void settle_SameGameRoomTwice_KeepFirstResult() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 0)
        ));
        when(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).thenReturn(Optional.of(gameRoom));
        when(gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of());

        // when
        GameNaturalDeathSettlementResult firstResult = service.settle(GAME_ROOM_ID, START_AT_MILLIS + 1_000L);
        GameNaturalDeathSettlementResult secondResult = service.settle(GAME_ROOM_ID, START_AT_MILLIS + 1_000L);

        // then
        assertThat(firstResult.status()).isEqualTo(GameNaturalDeathSettlementStatus.FINISHED);
        assertThat(secondResult.status()).isEqualTo(GameNaturalDeathSettlementStatus.NO_OP);
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(gameRoom.getResult()).isEqualTo(GameResult.DRAW);
        assertThat(gameRoom.getWinnerId()).isNull();
    }

    @Test
    @DisplayName("settle - 없는 gameRoom이면 no-op 처리한다")
    void settle_NotFound_NoOp() {
        // given
        when(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).thenReturn(Optional.empty());

        // when
        GameNaturalDeathSettlementResult result = service.settle(GAME_ROOM_ID, START_AT_MILLIS + 1_000L);

        // then
        assertThat(result.status()).isEqualTo(GameNaturalDeathSettlementStatus.NO_OP);
    }

    private GameRoom startedRoom(GameScenario scenario) {
        GameRoom gameRoom = readyRoom(scenario);
        gameRoom.start(LocalDateTime.ofInstant(Instant.ofEpochMilli(START_AT_MILLIS), ZoneOffset.UTC));
        return gameRoom;
    }

    private GameRoom readyRoom(GameScenario scenario) {
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .durationSeconds(1)
                .scenarioData(scenario)
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        return gameRoom;
    }

    private GameScenario scenario(HpStep... steps) {
        return GameScenario.of(List.of(steps));
    }
}
