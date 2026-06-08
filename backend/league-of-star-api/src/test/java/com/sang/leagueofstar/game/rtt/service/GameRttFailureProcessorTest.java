package com.sang.leagueofstar.game.rtt.service;

import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.game.rtt.domain.GameRttFailureReason;
import com.sang.leagueofstar.game.rtt.domain.GameRttState;
import com.sang.leagueofstar.game.rtt.domain.GameRttStatus;
import com.sang.leagueofstar.game.rtt.repository.GameRttMeasurementStore;
import com.sang.leagueofstar.game.websocket.service.GameStartFailedWebSocketSender;
import com.sang.leagueofstar.matching.command.MatchUserStatusCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRttFailureProcessorTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    @InjectMocks
    private GameRttFailureProcessor processor;

    @Mock
    private GameRttMeasurementStore gameRttMeasurementStore;

    @Mock
    private GameRttPingTracker gameRttPingTracker;

    @Mock
    private GameRoomReadService gameRoomReadService;

    @Mock
    private GameRoomCommandService gameRoomCommandService;

    @Mock
    private MatchUserStatusCommandService matchUserStatusCommandService;

    @Mock
    private GameStartFailedWebSocketSender gameStartFailedWebSocketSender;

    @Test
    @DisplayName("RTT 상태가 없으면 local ping만 정리한다")
    void processFailure_StateNotFound() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.empty());

        // when
        processor.processFailureWithLock(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED);

        // then
        verify(gameRttPingTracker).cleanup(GAME_ROOM_ID);
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("FAILED 유저가 없으면 실패 정산을 하지 않는다")
    void processFailure_NotFailed() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(
                rttState(GameRttStatus.PASSED, GameRttStatus.PASSED)
        ));

        // when
        processor.processFailureWithLock(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED);

        // then
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        verify(gameRttMeasurementStore, never()).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("READY gameRoom이면 ABORTED로 전환하고 match status와 RTT 상태를 정리한다")
    void processFailure_ReadyGameRoom_AbortAndCleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(
                rttState(GameRttStatus.FAILED, GameRttStatus.PENDING)
        ));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(gameRoomCommandService.abortReadyRoomIfReady(GAME_ROOM_ID)).thenReturn(true);

        // when
        processor.processFailureWithLock(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED);

        // then
        verify(gameRoomCommandService).abortReadyRoomIfReady(GAME_ROOM_ID);
        InOrder inOrder = inOrder(
                matchUserStatusCommandService,
                gameStartFailedWebSocketSender,
                gameRttPingTracker,
                gameRttMeasurementStore
        );
        inOrder.verify(matchUserStatusCommandService).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        inOrder.verify(gameStartFailedWebSocketSender)
                .sendFailure(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED.name(), "GO_TO_MATCH_START");
        inOrder.verify(gameRttPingTracker).cleanup(GAME_ROOM_ID);
        inOrder.verify(gameRttMeasurementStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("READY 조회 후 safe abort가 false이면 cleanup하지 않고 재시도 대상으로 둔다")
    void processFailure_ReadyAbortNoOp_DoNotCleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(
                rttState(GameRttStatus.FAILED, GameRttStatus.PENDING)
        ));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(gameRoomCommandService.abortReadyRoomIfReady(GAME_ROOM_ID)).thenReturn(false);

        // when
        processor.processFailureWithLock(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED);

        // then
        verify(matchUserStatusCommandService, never()).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        verify(gameRttMeasurementStore, never()).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("이미 ABORTED 상태이면 match status와 RTT 상태를 정리한다")
    void processFailure_AlreadyAborted_Cleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(
                rttState(GameRttStatus.FAILED, GameRttStatus.PENDING)
        ));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.ABORTED);

        // when
        processor.processFailureWithLock(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED);

        // then
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        verify(gameStartFailedWebSocketSender)
                .sendFailure(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED.name(), "GO_TO_MATCH_START");
        verify(gameRttMeasurementStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("이미 IN_PROGRESS이면 abort와 match status 제거 없이 RTT 상태만 정리한다")
    void processFailure_InProgress_CleanupOnly() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(
                rttState(GameRttStatus.FAILED, GameRttStatus.PENDING)
        ));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.IN_PROGRESS);

        // when
        processor.processFailureWithLock(GAME_ROOM_ID, GameRttFailureReason.RTT_FAILED);

        // then
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        verify(gameRttPingTracker).cleanup(GAME_ROOM_ID);
        verify(gameRttMeasurementStore).cleanup(GAME_ROOM_ID);
    }

    private GameRttState rttState(GameRttStatus userAStatus, GameRttStatus userBStatus) {
        return new GameRttState(
                GAME_ROOM_ID,
                USER_A_ID,
                USER_B_ID,
                userAStatus,
                userBStatus
        );
    }
}
