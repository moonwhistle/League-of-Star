package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.repository.GameActionRepository;
import com.sang.leagueofstar.domain.game.service.dto.GameActionSaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameActionCommandServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;

    private final GameActionRepository gameActionRepository = mock(GameActionRepository.class);
    private final GameActionCommandService service = new GameActionCommandService(gameActionRepository);

    @Test
    @DisplayName("save - 반복 LIGHTNING action을 그대로 저장하고 idempotent=false를 반환한다")
    void save_NewAction() {
        // given
        GameAction requested = action(1000L);
        when(gameActionRepository.saveAndFlush(requested)).thenReturn(requested);

        // when
        GameActionSaveResult result = service.save(requested);

        // then
        assertThat(result.action()).isEqualTo(requested);
        assertThat(result.idempotent()).isFalse();
        verify(gameActionRepository).saveAndFlush(requested);
    }

    private GameAction action(long serverReceiveTimeMs) {
        return GameAction.lightning(
                GAME_ROOM_ID,
                USER_ID,
                serverReceiveTimeMs,
                (int) serverReceiveTimeMs,
                1000
        );
    }
}
