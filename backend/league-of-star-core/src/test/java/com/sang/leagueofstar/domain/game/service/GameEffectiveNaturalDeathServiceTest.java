package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
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
    void calculateNaturalDeathAtMillis_NoLightning() {
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
    void calculateNaturalDeathAtMillis_FailedLightningDamage() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 2_000),
                new HpStep(2_000, 0)
        ));
        GameAction failedLightning = GameAction.lightning(
                GAME_ROOM_ID,
                FIRST_USER_ID,
                START_AT_MILLIS + 800L,
                800,
                3_000
        );

        // when
        long result = service.calculateNaturalDeathAtMillis(gameRoom, List.of(failedLightning));

        // then
        assertThat(result).isEqualTo(START_AT_MILLIS + 1_400L);
    }

    @Test
    @DisplayName("calculateNaturalDeathAtMillis - 여러 실패 SMITE의 누적 데미지를 반영한다")
    void calculateNaturalDeathAtMillis_MultipleFailedLightningDamage() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 3_000),
                new HpStep(2_000, 0)
        ));
        GameAction firstFailedLightning = GameAction.lightning(
                GAME_ROOM_ID,
                FIRST_USER_ID,
                START_AT_MILLIS + 700L,
                700,
                4_000
        );
        GameAction secondFailedLightning = GameAction.lightning(
                GAME_ROOM_ID,
                SECOND_USER_ID,
                START_AT_MILLIS + 900L,
                900,
                3_500
        );

        // when
        long result = service.calculateNaturalDeathAtMillis(
                gameRoom,
                List.of(firstFailedLightning, secondFailedLightning)
        );

        // then
        assertThat(result).isEqualTo(START_AT_MILLIS + 1_200L);
    }

    @Test
    @DisplayName("calculateNaturalDeathAtMillis - HP 하락이 없는 구간 이후의 자연사 시각을 계산한다")
    void calculateNaturalDeathAtMillis_PlateauThenDrop() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 10_000),
                new HpStep(2_000, 0)
        ));
        GameAction failedLightning = failedLightning(FIRST_USER_ID, 500L, 10_000);

        // when
        long result = service.calculateNaturalDeathAtMillis(gameRoom, List.of(failedLightning));

        // then
        assertThat(result).isEqualTo(START_AT_MILLIS + 1_880L);
    }

    @Test
    @DisplayName("calculateNaturalDeathAtMillis - scenario HP가 누적 SMITE 데미지와 같아지는 시각을 반환한다")
    void calculateNaturalDeathAtMillis_ExactDamageThreshold() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 1_200),
                new HpStep(2_000, 0)
        ));
        GameAction failedLightning = failedLightning(FIRST_USER_ID, 900L, 1_300);

        // when
        long result = service.calculateNaturalDeathAtMillis(gameRoom, List.of(failedLightning));
        int effectiveHp = service.calculateEffectiveHpAt(gameRoom, List.of(failedLightning), START_AT_MILLIS + 1_000L);

        // then
        assertThat(result).isEqualTo(START_AT_MILLIS + 1_000L);
        assertThat(effectiveHp).isZero();
    }

    @Test
    @DisplayName("calculateNaturalDeathAtMillis - 방어적으로 누적 SMITE 데미지가 시작 HP 이상이면 시작 시각을 반환한다")
    void calculateNaturalDeathAtMillis_DefensiveLightningDamageGreaterThanInitialHp() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 0)
        ));
        List<GameAction> actions = List.of(
                failedLightning(FIRST_USER_ID, 100L, 9_000),
                failedLightning(SECOND_USER_ID, 200L, 8_000),
                failedLightning(FIRST_USER_ID, 300L, 7_000),
                failedLightning(SECOND_USER_ID, 400L, 6_000),
                failedLightning(FIRST_USER_ID, 500L, 5_000),
                failedLightning(SECOND_USER_ID, 600L, 4_000),
                failedLightning(FIRST_USER_ID, 700L, 3_000),
                failedLightning(SECOND_USER_ID, 800L, 2_000),
                failedLightning(FIRST_USER_ID, 900L, 1_000)
        );

        // when
        long result = service.calculateNaturalDeathAtMillis(gameRoom, actions);
        int effectiveHp = service.calculateEffectiveHpAt(gameRoom, actions, START_AT_MILLIS);

        // then
        assertThat(result).isEqualTo(START_AT_MILLIS);
        assertThat(effectiveHp).isZero();
    }

    @Test
    @DisplayName("calculateNaturalDeathAtMillis - 같은 구간의 여러 SMITE는 action 순서와 무관하게 계산된다")
    void calculateNaturalDeathAtMillis_MultipleFailedLightningDamage_OrderIndependent() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 3_000),
                new HpStep(2_000, 0)
        ));
        GameAction firstFailedLightning = failedLightning(FIRST_USER_ID, 700L, 4_000);
        GameAction secondFailedLightning = failedLightning(SECOND_USER_ID, 900L, 3_500);

        // when
        long orderedResult = service.calculateNaturalDeathAtMillis(
                gameRoom,
                List.of(firstFailedLightning, secondFailedLightning)
        );
        long reversedResult = service.calculateNaturalDeathAtMillis(
                gameRoom,
                List.of(secondFailedLightning, firstFailedLightning)
        );

        // then
        assertThat(orderedResult).isEqualTo(START_AT_MILLIS + 1_200L);
        assertThat(reversedResult).isEqualTo(orderedResult);
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
        GameAction failedLightning = GameAction.lightning(
                GAME_ROOM_ID,
                FIRST_USER_ID,
                START_AT_MILLIS + 800L,
                800,
                3_000
        );

        // when
        int result = service.calculateEffectiveHpAt(gameRoom, List.of(failedLightning), START_AT_MILLIS + 1_000L);

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

    private GameAction failedLightning(Long userId, long offsetMillis, int dragonHpAtLightning) {
        return GameAction.lightning(
                GAME_ROOM_ID,
                userId,
                START_AT_MILLIS + offsetMillis,
                Math.toIntExact(offsetMillis),
                dragonHpAtLightning
        );
    }

    private GameScenario scenario(HpStep... steps) {
        return GameScenario.of(List.of(steps));
    }
}
