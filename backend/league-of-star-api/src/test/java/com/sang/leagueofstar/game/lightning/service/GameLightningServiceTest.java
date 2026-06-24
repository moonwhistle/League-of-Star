package com.sang.leagueofstar.game.lightning.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.GameRules;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameActionCommandService;
import com.sang.leagueofstar.domain.game.service.GameActionReadService;
import com.sang.leagueofstar.domain.game.service.GameLightningJudgementService;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.dto.GameActionSaveResult;
import com.sang.leagueofstar.game.end.service.GameEndDeadlineAdvanceService;
import com.sang.leagueofstar.game.lightning.domain.GameLightningCommand;
import com.sang.leagueofstar.game.record.service.GameRecordRankSettlementTrigger;
import com.sang.leagueofstar.game.result.domain.PracticeResult;
import com.sang.leagueofstar.game.result.service.GameResultPayloadFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class GameLightningServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final long SERVER_RECEIVE_TIME_MS = 3_200L;
    private static final Instant FINISHED_AT = Instant.parse("2026-05-21T03:00:00Z");

    private final GameRoomCommandService gameRoomCommandService = mock(GameRoomCommandService.class);
    private final GameActionReadService gameActionReadService = mock(GameActionReadService.class);
    private final GameActionCommandService gameActionCommandService = mock(GameActionCommandService.class);
    private final GameLightningJudgementService gameLightningJudgementService =
            mock(GameLightningJudgementService.class);
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
    @DisplayName("handleLightning - 비처치 LIGHTNING은 LIGHTNING_APPLIED payload를 반환하고 게임을 끝내지 않는다")
    void handleLightning_NonKill_ReturnAppliedOnly() {
        GameRoom gameRoom = mockInProgressRoom();
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 2_200, 1_300);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(action));
        when(gameLightningJudgementService.judge(gameRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.save(action)).thenReturn(GameActionSaveResult.saved(action));

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        assertThat(result).isPresent();
        assertThat(result.get().lightningApplied()).isNotNull();
        assertThat(result.get().lightningApplied().userId()).isEqualTo(USER_ID);
        assertThat(result.get().lightningApplied().damage()).isEqualTo(GameRules.LIGHTNING_DAMAGE);
        assertThat(result.get().lightningApplied().afterHp()).isEqualTo(100);
        assertThat(result.get().lightningApplied().isKill()).isFalse();
        assertThat(result.get().lightningApplied().cooldownUntil())
                .isEqualTo(SERVER_RECEIVE_TIME_MS + GameRules.LIGHTNING_COOLDOWN_MS);
        assertThat(result.get().gameResult()).isNull();
        verify(gameEndDeadlineAdvanceService).advanceAfterFailedLightning(gameRoom, List.of(action));
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(gameRoom);
    }

    @Test
    @DisplayName("handleLightning - 같은 유저의 마지막 LIGHTNING 이후 2초가 지나지 않으면 저장하지 않는다")
    void handleLightning_CooldownNotReady_ReturnEmpty() {
        GameRoom gameRoom = mockInProgressRoom();
        GameAction previousAction = GameAction.lightning(GAME_ROOM_ID, USER_ID, 2_000L, 1_000, 5_000);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(previousAction));

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, 3_900L));

        assertThat(result).isEmpty();
        verify(gameLightningJudgementService, never()).judge(gameRoom, USER_ID, 3_900L, List.of(previousAction));
        verify(gameActionCommandService, never()).save(previousAction);
    }

    @Test
    @DisplayName("handleLightning - LIGHTNING 처치이면 LIGHTNING_APPLIED와 GAME_RESULT를 함께 반환한다")
    void handleLightning_Kill_ReturnAppliedAndGameResult() {
        GameRoom lockedRoom = mockInProgressRoom();
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 2_200, 1_000);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(action));
        when(gameLightningJudgementService.judge(lockedRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.save(action)).thenReturn(GameActionSaveResult.saved(action));
        when(gameRoomCommandService.finishInProgressRoomByLightningKill(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(finishedRoom));
        when(finishedRoom.isMatchMode()).thenReturn(true);
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        assertThat(result).isPresent();
        assertThat(result.get().lightningApplied().isKill()).isTrue();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().gameResultBroadcast()).isTrue();
        assertThat(result.get().gameResult().reason()).isEqualTo("LIGHTNING_KILL");
        assertThat(result.get().gameResult().winnerUserId()).isEqualTo(USER_ID);
        verify(gameRecordRankSettlementTrigger).settleFinishedGameRoomAfterCommit(finishedRoom);
        verify(gameEndDeadlineAdvanceService, never()).advanceAfterFailedLightning(lockedRoom, List.of(action));
    }

    @Test
    @DisplayName("handleLightning - PRACTICE 처치이면 practice SUCCESS GAME_RESULT를 반환한다")
    void handleLightning_PracticeKill_ReturnPracticeSuccessGameResult() {
        GameRoom lockedRoom = mockInProgressRoom();
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 2_200, 1_000);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(action));
        when(gameLightningJudgementService.judge(lockedRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.save(action)).thenReturn(GameActionSaveResult.saved(action));
        when(gameRoomCommandService.finishInProgressRoomByLightningKill(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(finishedRoom));
        when(finishedRoom.isPracticeMode()).thenReturn(true);
        when(finishedRoom.getGameMode()).thenReturn(GameMode.PRACTICE);
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        assertThat(result).isPresent();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().gameResult().gameMode()).isEqualTo(GameMode.PRACTICE);
        assertThat(result.get().gameResult().reason()).isEqualTo("PRACTICE_LIGHTNING_KILL");
        assertThat(result.get().gameResult().practiceResult()).isEqualTo(PracticeResult.SUCCESS);
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(finishedRoom);
    }

    @Test
    @DisplayName("handleLightning - CUSTOM 처치이면 기존 reason과 CUSTOM gameMode GAME_RESULT를 반환한다")
    void handleLightning_CustomKill_ReturnCustomGameResult() {
        GameRoom lockedRoom = mockInProgressRoom();
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 2_200, 1_000);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(action));
        when(gameLightningJudgementService.judge(lockedRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.of(action));
        when(gameActionCommandService.save(action)).thenReturn(GameActionSaveResult.saved(action));
        when(gameRoomCommandService.finishInProgressRoomByLightningKill(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(finishedRoom));
        when(finishedRoom.isPracticeMode()).thenReturn(false);
        when(finishedRoom.isMatchMode()).thenReturn(false);
        when(finishedRoom.getGameMode()).thenReturn(GameMode.CUSTOM);
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        assertThat(result).isPresent();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().gameResultBroadcast()).isTrue();
        assertThat(result.get().gameResult().gameMode()).isEqualTo(GameMode.CUSTOM);
        assertThat(result.get().gameResult().reason()).isEqualTo("LIGHTNING_KILL");
        assertThat(result.get().gameResult().practiceResult()).isNull();
        assertThat(result.get().gameResult().winnerUserId()).isEqualTo(USER_ID);
        verify(gameRecordRankSettlementTrigger).settleFinishedGameRoomAfterCommit(finishedRoom);
    }

    @Test
    @DisplayName("handleLightning - 이미 FINISHED인 gameRoom이면 action 저장 없이 현재 session용 GAME_RESULT만 반환한다")
    void handleLightning_AlreadyFinished_ReturnCurrentGameResultOnly() {
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 2_200, 1_000);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID)).thenReturn(finishedRoom);
        when(finishedRoom.getStatus()).thenReturn(GameStatus.FINISHED);
        when(finishedRoom.getGameMode()).thenReturn(GameMode.MATCH);
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(action));

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        assertThat(result).isPresent();
        assertThat(result.get().lightningApplied()).isNull();
        assertThat(result.get().gameResult()).isNotNull();
        assertThat(result.get().gameResultBroadcast()).isFalse();
        assertThat(result.get().gameResult().reason()).isEqualTo("LIGHTNING_KILL");
        verify(gameRecordRankSettlementTrigger, never()).settleFinishedGameRoomAfterCommit(finishedRoom);
    }

    @Test
    @DisplayName("handleLightning - 이미 FINISHED인 PRACTICE gameRoom이면 현재 practice GAME_RESULT를 반환한다")
    void handleLightning_AlreadyFinishedPractice_ReturnCurrentPracticeGameResultOnly() {
        GameRoom finishedRoom = mock(GameRoom.class);
        GameAction action = GameAction.lightning(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS, 2_200, 1_000);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID)).thenReturn(finishedRoom);
        when(finishedRoom.getStatus()).thenReturn(GameStatus.FINISHED);
        when(finishedRoom.isPracticeMode()).thenReturn(true);
        when(finishedRoom.getGameMode()).thenReturn(GameMode.PRACTICE);
        when(finishedRoom.getResult()).thenReturn(GameResult.PLAYER1_WIN);
        when(finishedRoom.getWinnerId()).thenReturn(USER_ID);
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of(action));

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        assertThat(result).isPresent();
        assertThat(result.get().lightningApplied()).isNull();
        assertThat(result.get().gameResultBroadcast()).isFalse();
        assertThat(result.get().gameResult().gameMode()).isEqualTo(GameMode.PRACTICE);
        assertThat(result.get().gameResult().reason()).isEqualTo("PRACTICE_LIGHTNING_KILL");
        assertThat(result.get().gameResult().practiceResult()).isEqualTo(PracticeResult.SUCCESS);
    }

    @Test
    @DisplayName("handleLightning - 판정 대상 action이 없으면 저장하지 않고 empty를 반환한다")
    void handleLightning_NoJudgedAction_ReturnEmpty() {
        GameRoom gameRoom = mockInProgressRoom();
        when(gameActionReadService.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(GAME_ROOM_ID))
                .thenReturn(List.of());
        when(gameLightningJudgementService.judge(gameRoom, USER_ID, SERVER_RECEIVE_TIME_MS, List.of()))
                .thenReturn(Optional.empty());

        var result = service.handleLightning(new GameLightningCommand(GAME_ROOM_ID, USER_ID, SERVER_RECEIVE_TIME_MS));

        assertThat(result).isEmpty();
        verify(gameActionCommandService, never()).save(any());
    }

    private GameRoom mockInProgressRoom() {
        GameRoom gameRoom = mock(GameRoom.class);
        when(gameRoomCommandService.lockLightningResultRoom(GAME_ROOM_ID, USER_ID)).thenReturn(gameRoom);
        when(gameRoom.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        return gameRoom;
    }
}
