package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.HpStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameEffectiveNaturalDeathServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final long START_AT_MILLIS = 10_000L;

    private final GameEffectiveNaturalDeathService service = new GameEffectiveNaturalDeathService();

    @Test
    @DisplayName("calculateNaturalDeathAtMillis - SMITE가 없으면 scenario 마지막 시각을 반환한다")
    void calculateNaturalDeathAtMillis_NoSmite() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 5_000),
                new HpStep(2_000, 0)
        ));

        // when
        long result = service.calculateNaturalDeathAtMillis(gameRoom, List.of());

        // then
        assertThat(result).isEqualTo(START_AT_MILLIS + 2_000L);
    }

    @Test
    @DisplayName("calculateNaturalDeathAtMillis - 실패 SMITE 데미지를 반영해 자연사 시각을 앞당긴다")
    void calculateNaturalDeathAtMillis_FailedSmiteDamage() {
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

        // when
        long result = service.calculateNaturalDeathAtMillis(gameRoom, List.of(failedSmite));

        // then
        assertThat(result).isEqualTo(START_AT_MILLIS + 1_400L);
    }

    @Test
    @DisplayName("calculateEffectiveHpAt - 특정 시점 scenario HP에서 누적 SMITE 데미지를 뺀다")
    void calculateEffectiveHpAt() {
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

        // when
        int result = service.calculateEffectiveHpAt(gameRoom, List.of(failedSmite), START_AT_MILLIS + 1_000L);

        // then
        assertThat(result).isEqualTo(800);
    }

    private GameRoom startedRoom(GameScenario scenario) {
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .durationSeconds(2)
                .scenarioData(scenario)
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.ofInstant(Instant.ofEpochMilli(START_AT_MILLIS), ZoneOffset.UTC));
        return gameRoom;
    }

    private GameScenario scenario(HpStep... steps) {
        return GameScenario.of(List.of(steps));
    }
}
