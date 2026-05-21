package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.service.GameActionReadService;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameSmiteServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;

    private final GameRoomCommandService gameRoomCommandService = mock(GameRoomCommandService.class);
    private final GameActionReadService gameActionReadService = mock(GameActionReadService.class);
    private final GameSmiteService service = new GameSmiteService(gameRoomCommandService, gameActionReadService);

    @Test
    @DisplayName("handleSmite - 기존 action이 있으면 idempotent SMITE_RESULT payload를 반환한다")
    void handleSmite_ExistingAction_ReturnIdempotentPayload() {
        // given
        GameAction action = GameAction.builder()
                .gameRoomId(GAME_ROOM_ID)
                .userId(USER_ID)
                .serverReceiveTimeMs(1000L)
                .smiteTimeMs(900)
                .dragonHpAtSmite(1000)
                .isKill(true)
                .build();
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(action));

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, 1200L));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.get().userId()).isEqualTo(USER_ID);
        assertThat(result.get().damage()).isEqualTo(GameRules.SMITE_DAMAGE);
        assertThat(result.get().afterHp()).isZero();
        assertThat(result.get().isKill()).isTrue();
        assertThat(result.get().idempotent()).isTrue();

        InOrder inOrder = inOrder(gameRoomCommandService, gameActionReadService);
        inOrder.verify(gameRoomCommandService).lockInProgressRoomForSmite(GAME_ROOM_ID, USER_ID);
        inOrder.verify(gameActionReadService).findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("handleSmite - 기존 action이 없으면 이후 판정 단계로 넘기기 위해 empty를 반환한다")
    void handleSmite_NoExistingAction_ReturnEmpty() {
        // given
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, 1200L));

        // then
        assertThat(result).isEmpty();
    }
}
