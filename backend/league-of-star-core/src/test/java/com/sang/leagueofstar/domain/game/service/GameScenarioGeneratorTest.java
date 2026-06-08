package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GameScenarioGeneratorTest {

    private static final int DURATION_SECONDS = 8;
    private static final long DURATION_MILLIS = 8_000L;
    private static final int EXPECTED_STEP_COUNT = 41;

    private final GameScenarioGenerator generator = new GameScenarioGenerator();

    @Test
    @DisplayName("generate - 200ms 단위 랜덤 burst HP 시나리오를 생성한다")
    void generate_CreateRandomBurstScenario() {
        // when
        GameScenario scenario = generator.generate(DURATION_SECONDS);

        // then
        List<HpStep> steps = scenario.steps();
        assertThat(steps).hasSize(EXPECTED_STEP_COUNT);
        assertThat(steps.get(0).timeMs()).isZero();
        assertThat(steps.get(0).hp()).isEqualTo(GameRoom.DEFAULT_DRAGON_MAX_HP);
        assertThat(steps.get(steps.size() - 1).timeMs()).isEqualTo(DURATION_MILLIS);
        assertThat(steps.get(steps.size() - 1).hp()).isZero();
    }

    @Test
    @DisplayName("generate - HP는 증가하지 않고 0~10000 범위에 머문다")
    void generate_HpNeverIncreases() {
        // when
        GameScenario scenario = generator.generate(DURATION_SECONDS);

        // then
        List<HpStep> steps = scenario.steps();
        for (int index = 1; index < steps.size(); index++) {
            HpStep previous = steps.get(index - 1);
            HpStep current = steps.get(index);
            assertThat(current.timeMs()).isGreaterThan(previous.timeMs());
            assertThat(current.hp()).isBetween(0, GameRoom.DEFAULT_DRAGON_MAX_HP);
            assertThat(current.hp()).isLessThanOrEqualTo(previous.hp());
        }
    }

    @Test
    @DisplayName("generate - 정체 구간과 burst 구간을 포함한다")
    void generate_ContainsPlateauAndBurst() {
        // when
        GameScenario scenario = generator.generate(DURATION_SECONDS);

        // then
        List<Integer> damages = damages(scenario.steps());
        List<Integer> positiveDamages = damages.stream()
                .filter(damage -> damage > 0)
                .toList();
        assertThat(damages).contains(0);
        assertThat(positiveDamages).isNotEmpty();
        assertThat(positiveDamages.stream().mapToInt(Integer::intValue).max().orElseThrow())
                .isGreaterThan(positiveDamages.stream().mapToInt(Integer::intValue).min().orElseThrow());
    }

    private List<Integer> damages(List<HpStep> steps) {
        return IntStream.range(1, steps.size())
                .map(index -> steps.get(index - 1).hp() - steps.get(index).hp())
                .boxed()
                .toList();
    }
}
