package com.sang.smite.game.start.dto;

import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.HpStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameStartScenarioPayloadTest {

    @Test
    @DisplayName("from - HP 시나리오를 GAME_START payload로 변환한다")
    void from() {
        // given
        GameScenario scenarioData = GameScenario.of(List.of(
                new HpStep(0L, 10000),
                new HpStep(1000L, 9000),
                new HpStep(2000L, 0)
        ));

        // when
        GameStartScenarioPayload result = GameStartScenarioPayload.from(scenarioData);

        // then
        assertThat(result.dragonMaxHp()).isEqualTo(10000);
        assertThat(result.durationMs()).isEqualTo(2000L);
        assertThat(result.hpTimeline()).containsExactly(
                new GameStartScenarioPayload.HpTimelineStep(0L, 10000),
                new GameStartScenarioPayload.HpTimelineStep(1000L, 9000),
                new GameStartScenarioPayload.HpTimelineStep(2000L, 0)
        );
    }
}
