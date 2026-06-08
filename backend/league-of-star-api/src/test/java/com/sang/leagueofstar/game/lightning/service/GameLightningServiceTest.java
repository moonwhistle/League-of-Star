package com.sang.leagueofstar.game.lightning.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameActionCommandService;
import com.sang.leagueofstar.domain.game.service.GameActionReadService;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameLightningJudgementService;
import com.sang.leagueofstar.domain.game.service.dto.GameActionSaveResult;
import com.sang.leagueofstar.game.end.service.GameEndDeadlineAdvanceService;
import com.sang.leagueofstar.game.record.service.GameRecordRankSettlementTrigger;
import com.sang.leagueofstar.game.result.service.GameResultPayloadFactory;
import com.sang.leagueofstar.game.lightning.domain.GameLightningCommand;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameLightningServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final long SERVER_RECEIVE_TIME_MS = 1_200L;
    private static final Instant FINISHED_AT = Instant.parse("2026-05-21T03:00:00Z");

    private final GameRoomCommandService gameRoomCommandService = mock(GameRoomCommandService.class);
    private final GameActionReadService gameActionReadService = mock(GameActionReadService.class);
    private final GameActionCommandService gameActionCommandService = mock(GameActionCommandService.class);
    private final GameLightningJudgementService gameLightningJudgementService = mock(GameLightningJudgementService.class);
    private final GameEndDeadlineAdvanceService gameEndDeadlineAdvanceService =
            mock(GameEndDeadlineAdvanceService.class);
    private final GameRecordRankSettlementTrigger gameRecordRankSettlementTrigger =
            mock(GameRecordRankSettlementTrigger.class);
    private final GameResultPayloadFactory gameResultPayloadFactory = new GameResultPayloadFactory();
    private final Clock clock = Clock.fixed(FINISHED_AT, ZoneOffset.UTC);
    private final GameLightningService service = new GameLightningService(
            gameRoomCommandService,
            gameActionReadService,
            gameActionCommandService,
            gameLightningJudgementService,
            gameEndDeadlineAdvanceService,
            gameRecordRankSettlementTrigger,
            gameResultPayloadFactory,
            clock
    );

    @Test
    @DisplayName("handleLightning - 기존 action이 있고 게임이 진행 중이면 중간 응답을 반환하지 않는다")
    void handleLightning_ExistingActionInProgress_ReturnEmpty() {
        // given
        GameRoom gameRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, 1000L, 900, 1000);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(action));

        // when
        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isEmpty();

        InOrder inOrder = inOrder(gameRoomCommandService, gameActionReadService);
        inOrder.verify(gameRoomCommandService).lockLightningResultRoom(GAME_ROOM_ID, USER_ID);
        inOrder.verify(gameActionReadService).findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("handleLightning - 신규 LIGHTNING가 처치하지 못하고 게임이 진행 중이면 중간 응답을 반환하지 않는다")
    void handleLightning_NewNonKillAction_SaveAndReturnEmpty() {
        // given
        GameRoom gameRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 200, 1300);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(action));
        when(gameLightningJudgementService.judge(gameRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.saveIfAbsent(action))
                .thenReturn(GameActionSaveResult.saved(action));

        // when
        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isEmpty();

        InOrder inOrder = inOrder(
                gameRoomCommandService,
                gameActionReadService,
                gameLightningJudgementService,
                gameActionCommandService
        );
        inOrder.verify(gameRoomCommandService).lockLightningResultRoom(GAME_ROOM_ID, USER_ID);
        inOrder.verify(gameActionReadService).findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID);
        inOrder.verify(gameActionReadService).findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID);
        inOrder.verify(gameLightningJudgementService).judge(gameRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of());
        inOrder.verify(gameActionCommandService).saveIfAbsent(action);
        verify(gameEndDeadlineAdvanceService).advanceAfterFailedLightning(gameRoom, List.of(action));
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(gameRoom);
    }

    @Test
    @DisplayName("handleLightning - LIGHTNING로 처치하면 gameRoom을 FINISHED로 전환하고 GAME_RESULT payload를 반환한다")
    void handleLightning_Kill_FinishGameAndReturnGameResult() {
        // given
        GameRoom lockedRoom = mock(GameRoom.class);
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 200, 1000);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(lockedRoom);
        when(lockedRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(action));
        when(gameLightningJudgementService.judge(lockedRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.saveIfAbsent(action))
                .thenReturn(GameActionSaveResult.saved(action));
        when(gameRoomCommandService.finishInProgressRoomByLightningKill(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(finishedRoom));
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);

        // when
        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().broadcast()).isTrue();
        assertThat(result.get().gameResult().gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.get().gameResult().result()).isEqualTo(GameResult.PLAYER1_WIN);
        assertThat(result.get().gameResult().winnerUserId()).isEqualTo(USER_ID);
        assertThat(result.get().gameResult().reason()).isEqualTo("LIGHTNING_KILL");
        assertThat(result.get().gameResult().finishedAt()).isEqualTo(FINISHED_AT.toEpochMilli());
        assertThat(result.get().gameResult().actions()).hasSize(1);
        verify(gameRecordRankSettlementTrigger).settleFinishedGameRoomAfterCommit(finishedRoom);
        verify(gameEndDeadlineAdvanceService, never()).advanceAfterFailedLightning(lockedRoom, List.of(action));
    }

    @Test
    @DisplayName("handleLightning - 두 유저가 모두 실패 LIGHTNING를 사용하면 DRAW GAME_RESULT payload를 반환한다")
    void handleLightning_BothUsersFailedLightning_FinishDrawAndReturnGameResult() {
        // given
        GameRoom lockedRoom = mock(GameRoom.class);
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction firstAction = GameAction.lightning(GAME_ROOM_ID, OTHER_USER_ID, 1_000L, 100, 5_000);
        GameAction secondAction = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 200, 2_500);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(lockedRoom);
        when(lockedRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(firstAction))
                .thenReturn(List.of(firstAction, secondAction));
        when(gameLightningJudgementService.judge(
                lockedRoom,
                USER_ID,
                SERVER_RECEIVE_TIME_MS,
                List.of(firstAction)
        )).thenReturn(Optional.of(secondAction));
        when(gameActionCommandService.saveIfAbsent(secondAction))
                .thenReturn(GameActionSaveResult.saved(secondAction));
        when(gameRoomCommandService.finishInProgressRoomByBothLightningsUsedDraw(GAME_ROOM_ID))
                .thenReturn(Optional.of(finishedRoom));
        when(finishedRoom.getResult()).thenReturn(GameResult.DRAW);
        when(finishedRoom.getWinnerId()).thenReturn(null);

        // when
        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().broadcast()).isTrue();
        assertThat(result.get().gameResult().result()).isEqualTo(GameResult.DRAW);
        assertThat(result.get().gameResult().winnerUserId()).isNull();
        assertThat(result.get().gameResult().reason()).isEqualTo("BOTH_LIGHTNINGS_USED_DRAW");
        assertThat(result.get().gameResult().finishedAt()).isEqualTo(FINISHED_AT.toEpochMilli());
        assertThat(result.get().gameResult().actions()).hasSize(2);
        verify(gameRecordRankSettlementTrigger).settleFinishedGameRoomAfterCommit(finishedRoom);
        verify(gameEndDeadlineAdvanceService, never())
                .advanceAfterFailedLightning(lockedRoom, List.of(firstAction, secondAction));
    }

    @Test
    @DisplayName("handleLightning - 이미 FINISHED인 gameRoom이면 action 저장 없이 GAME_RESULT만 반환한다")
    void handleLightning_AlreadyFinished_ReturnCurrentGameResultOnly() {
        // given
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 200, 1000);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(finishedRoom);
        when(finishedRoom.getStatus()).thenReturn(GameStatus.FINISHED);
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(action));

        // when
        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().broadcast()).isFalse();
        assertThat(result.get().gameResult().result()).isEqualTo(GameResult.PLAYER1_WIN);
        assertThat(result.get().gameResult().winnerUserId()).isEqualTo(USER_ID);
        assertThat(result.get().gameResult().reason()).isEqualTo("LIGHTNING_KILL");
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(finishedRoom);
    }

    @Test
    @DisplayName("handleLightning - 이미 자연사 DRAW로 FINISHED인 gameRoom이면 NATURAL_DEATH_DRAW reason을 반환한다")
    void handleLightning_AlreadyFinishedNaturalDeathDraw_ReturnCurrentGameResultOnly() {
        // given
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 200, 3_000);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(finishedRoom);
        when(finishedRoom.getStatus()).thenReturn(GameStatus.FINISHED);
        when(finishedRoom.getResult()).thenReturn(GameResult.DRAW);
        when(finishedRoom.getWinnerId()).thenReturn(null);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(action));

        // when
        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isPresent();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().broadcast()).isFalse();
        assertThat(result.get().gameResult().result()).isEqualTo(GameResult.DRAW);
        assertThat(result.get().gameResult().winnerUserId()).isNull();
        assertThat(result.get().gameResult().reason()).isEqualTo("NATURAL_DEATH_DRAW");
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(finishedRoom);
    }

    @Test
    @DisplayName("handleLightning - 판정 대상 action이 없으면 저장하지 않고 empty를 반환한다")
    void handleLightning_NoJudgedAction_ReturnEmpty() {
        // given
        GameRoom gameRoom = mock(GameRoom.class);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID))
                .thenReturn(gameRoom);
        when(gameRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(gameActionReadService.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of());
        when(gameLightningJudgementService.judge(gameRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.empty());

        // when
        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        // then
        assertThat(result).isEmpty();
    }
}
