package com.sang.leagueofstar.game.start.service;

import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameStartScenarioServiceTest {

    private static final Long GAME_ROOM_ID = 100L;

    private final GameRoomReadService gameRoomReadService = mock(GameRoomReadService.class);
    private final GameStartScenarioService service = new GameStartScenarioService(gameRoomReadService);

    @Test
    @DisplayName("getScenarioPayload - gameRoom에 저장된 HP 시나리오를 payload로 반환한다")
    void getScenarioPayload() {
        // given
        GameScenario scenarioData = GameScenario.of(List.of(
                new HpStep(0L, 10000),
                new HpStep(1000L, 9000)
        ));
        when(gameRoomReadService.getScenarioData(GAME_ROOM_ID)).thenReturn(scenarioData);

        // when
        GameStartScenarioPayload result = service.getScenarioPayload(GAME_ROOM_ID);

        // then
        assertThat(result.dragonMaxHp()).isEqualTo(10000);
        assertThat(result.durationMs()).isEqualTo(1000L);
        assertThat(result.hpTimeline()).containsExactly(
                new GameStartScenarioPayload.HpTimelineStep(0L, 10000),
                new GameStartScenarioPayload.HpTimelineStep(1000L, 9000)
        );
    }
}
