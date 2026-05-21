package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.service.GameActionCommandService;
import com.sang.smite.domain.game.service.GameActionReadService;
import com.sang.smite.domain.game.service.GameActionSaveResult;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameSmiteJudgementService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameSmiteServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final long SERVER_RECEIVE_TIME_MS = 1_200L;

    private final GameRoomCommandService gameRoomCommandService = mock(GameRoomCommandService.class);
    private final GameActionReadService gameActionReadService = mock(GameActionReadService.class);
    private final GameActionCommandService gameActionCommandService = mock(GameActionCommandService.class);
    private final GameSmiteJudgementService gameSmiteJudgementService = mock(GameSmiteJudgementService.class);
    private final GameSmiteService service = new GameSmiteService(
            gameRoomCommandService,
            gameActionReadService,
            gameActionCommandService,
            gameSmiteJudgementService
    );

    @Test
    @DisplayName("handleSmite - 기존 action이 있으면 idempotent SMITE_RESULT payload를 반환한다")
    void handleSmite_ExistingAction_ReturnIdempotentPayload() {
        // given
        GameRoom gameRoom = mock(GameRoom.class);
        GameAction action = GameAction.builder()
                .gameRoomId(GAME_ROOM_ID)
                .userId(USER_ID)
                .serverReceiveTimeMs(1000L)
                .smiteTimeMs(900)
                .dragonHpAtSmite(1000)
                .isKill(true)
                .build();
        when(gameRoomCommandService.lockInProgressRoomForSmite(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(action));

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

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
    @DisplayName("handleSmite - 신규 SMITE이면 판정 action을 저장하고 SMITE_RESULT payload를 반환한다")
    void handleSmite_NewAction_SaveAndReturnPayload() {
        // given
        GameRoom gameRoom = mock(GameRoom.class);
        GameAction action = GameAction.builder()
                .gameRoomId(GAME_ROOM_ID)
                .userId(USER_ID)
                .serverReceiveTimeMs(SERVER_RECEIVE_TIME_MS)
                .smiteTimeMs(200)
                .dragonHpAtSmite(1300)
                .isKill(false)
                .build();
        when(gameRoomCommandService.lockInProgressRoomForSmite(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of());
        when(gameSmiteJudgementService.judge(gameRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.saveIfAbsent(action))
                .thenReturn(GameActionSaveResult.saved(action));

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().serverReceiveTime()).isEqualTo(SERVER_RECEIVE_TIME_MS);
        assertThat(result.get().smiteTimeMs()).isEqualTo(200);
        assertThat(result.get().dragonHpAtSmite()).isEqualTo(1300);
        assertThat(result.get().afterHp()).isEqualTo(100);
        assertThat(result.get().isKill()).isFalse();
        assertThat(result.get().idempotent()).isFalse();
    }

    @Test
    @DisplayName("handleSmite - 판정 대상 action이 없으면 저장하지 않고 empty를 반환한다")
    void handleSmite_NoJudgedAction_ReturnEmpty() {
        // given
        GameRoom gameRoom = mock(GameRoom.class);
        when(gameRoomCommandService.lockInProgressRoomForSmite(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of());
        when(gameSmiteJudgementService.judge(gameRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.empty());

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isEmpty();
    }
}
