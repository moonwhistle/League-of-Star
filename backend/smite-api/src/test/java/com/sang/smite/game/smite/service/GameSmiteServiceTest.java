package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.service.GameActionCommandService;
import com.sang.smite.domain.game.service.GameActionReadService;
import com.sang.smite.domain.game.service.GameActionSaveResult;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameSmiteJudgementService;
import com.sang.smite.game.smite.domain.GameSmiteCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private static final Instant FINISHED_AT = Instant.parse("2026-05-21T03:00:00Z");

    private final GameRoomCommandService gameRoomCommandService = mock(GameRoomCommandService.class);
    private final GameActionReadService gameActionReadService = mock(GameActionReadService.class);
    private final GameActionCommandService gameActionCommandService = mock(GameActionCommandService.class);
    private final GameSmiteJudgementService gameSmiteJudgementService = mock(GameSmiteJudgementService.class);
    private final GameResultPayloadFactory gameResultPayloadFactory = new GameResultPayloadFactory();
    private final Clock clock = Clock.fixed(FINISHED_AT, ZoneOffset.UTC);
    private final GameSmiteService service = new GameSmiteService(
            gameRoomCommandService,
            gameActionReadService,
            gameActionCommandService,
            gameSmiteJudgementService,
            gameResultPayloadFactory,
            clock
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
        when(gameRoomCommandService.lockSmiteResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(action));

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().smiteResult().gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.get().smiteResult().userId()).isEqualTo(USER_ID);
        assertThat(result.get().smiteResult().damage()).isEqualTo(GameRules.SMITE_DAMAGE);
        assertThat(result.get().smiteResult().afterHp()).isZero();
        assertThat(result.get().smiteResult().isKill()).isTrue();
        assertThat(result.get().smiteResult().idempotent()).isTrue();
        assertThat(result.get().gameResult()).isNull();

        InOrder inOrder = inOrder(gameRoomCommandService, gameActionReadService);
        inOrder.verify(gameRoomCommandService).lockSmiteResultRoom(GAME_ROOM_ID, USER_ID);
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
        when(gameRoomCommandService.lockSmiteResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
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
        assertThat(result.get().smiteResult().serverReceiveTime()).isEqualTo(SERVER_RECEIVE_TIME_MS);
        assertThat(result.get().smiteResult().smiteTimeMs()).isEqualTo(200);
        assertThat(result.get().smiteResult().dragonHpAtSmite()).isEqualTo(1300);
        assertThat(result.get().smiteResult().afterHp()).isEqualTo(100);
        assertThat(result.get().smiteResult().isKill()).isFalse();
        assertThat(result.get().smiteResult().idempotent()).isFalse();
        assertThat(result.get().gameResult()).isNull();
    }

    @Test
    @DisplayName("handleSmite - SMITE로 처치하면 gameRoom을 FINISHED로 전환하고 GAME_RESULT payload를 반환한다")
    void handleSmite_Kill_FinishGameAndReturnGameResult() {
        // given
        GameRoom lockedRoom = mock(GameRoom.class);
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.builder()
                .gameRoomId(GAME_ROOM_ID)
                .userId(USER_ID)
                .serverReceiveTimeMs(SERVER_RECEIVE_TIME_MS)
                .smiteTimeMs(200)
                .dragonHpAtSmite(1000)
                .isKill(true)
                .build();
        when(gameRoomCommandService.lockSmiteResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(lockedRoom);
        when(lockedRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(action));
        when(gameSmiteJudgementService.judge(lockedRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.saveIfAbsent(action))
                .thenReturn(GameActionSaveResult.saved(action));
        when(gameRoomCommandService.finishInProgressRoomBySmiteKill(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(finishedRoom));
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().smiteResult().afterHp()).isZero();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().gameResult().gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.get().gameResult().result()).isEqualTo(GameResult.PLAYER1_WIN);
        assertThat(result.get().gameResult().winnerUserId()).isEqualTo(USER_ID);
        assertThat(result.get().gameResult().reason()).isEqualTo("SMITE_KILL");
        assertThat(result.get().gameResult().finishedAt()).isEqualTo(FINISHED_AT.toEpochMilli());
        assertThat(result.get().gameResult().actions()).hasSize(1);
    }

    @Test
    @DisplayName("handleSmite - 이미 FINISHED인 gameRoom이면 action 저장 없이 GAME_RESULT만 반환한다")
    void handleSmite_AlreadyFinished_ReturnCurrentGameResultOnly() {
        // given
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.builder()
                .gameRoomId(GAME_ROOM_ID)
                .userId(USER_ID)
                .serverReceiveTimeMs(SERVER_RECEIVE_TIME_MS)
                .smiteTimeMs(200)
                .dragonHpAtSmite(1000)
                .isKill(true)
                .build();
        when(gameRoomCommandService.lockSmiteResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(finishedRoom);
        when(finishedRoom.getStatus()).thenReturn(GameStatus.FINISHED);
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(action));

        // when
        var result = service.handleSmite(new GameSmiteCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().smiteResult()).isNull();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().gameResult().result()).isEqualTo(GameResult.PLAYER1_WIN);
        assertThat(result.get().gameResult().winnerUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("handleSmite - 판정 대상 action이 없으면 저장하지 않고 empty를 반환한다")
    void handleSmite_NoJudgedAction_ReturnEmpty() {
        // given
        GameRoom gameRoom = mock(GameRoom.class);
        when(gameRoomCommandService.lockSmiteResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
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
