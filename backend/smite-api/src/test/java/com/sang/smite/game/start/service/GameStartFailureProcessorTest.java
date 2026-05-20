package com.sang.smite.game.start.service;

import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.end.service.GameEndScheduleService;
import com.sang.smite.game.rtt.domain.GameRttState;
import com.sang.smite.game.rtt.domain.GameRttStatus;
import com.sang.smite.game.rtt.repository.GameRttMeasurementStore;
import com.sang.smite.game.rtt.service.GameRttPingTracker;
import com.sang.smite.game.start.common.constant.GameStartConstants;
import com.sang.smite.game.start.domain.GameStartFailureReason;
import com.sang.smite.game.waiting.repository.GameWaitingStore;
import com.sang.smite.game.websocket.service.GameStartFailedWebSocketSender;
import com.sang.smite.matching.command.MatchUserStatusCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class GameStartFailureProcessorTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    @InjectMocks
    private GameStartFailureProcessor processor;

    @Mock
    private GameRoomReadService gameRoomReadService;

    @Mock
    private GameRoomCommandService gameRoomCommandService;

    @Mock
    private GameRttMeasurementStore gameRttMeasurementStore;

    @Mock
    private GameRttPingTracker gameRttPingTracker;

    @Mock
    private GameWaitingStore gameWaitingStore;

    @Mock
    private GameEndScheduleService gameEndScheduleService;

    @Mock
    private MatchUserStatusCommandService matchUserStatusCommandService;

    @Mock
    private GameStartFailedWebSocketSender gameStartFailedWebSocketSender;

    @Test
    @DisplayName("processStartedFailure - IN_PROGRESS gameRoom을 abort하고 상태 저장소와 WebSocket을 정리한다")
    void processStartedFailure_AbortAndCleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(true);

        // when
        processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_END_DEADLINE_REGISTRATION_FAILED,
                true
        );

        // then
        InOrder inOrder = inOrder(
                gameRoomCommandService,
                matchUserStatusCommandService,
                gameStartFailedWebSocketSender,
                gameRttPingTracker,
                gameRttMeasurementStore,
                gameWaitingStore
        );
        inOrder.verify(gameRoomCommandService).abortInProgressRoomIfInProgress(GAME_ROOM_ID);
        inOrder.verify(matchUserStatusCommandService).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        inOrder.verify(gameStartFailedWebSocketSender).sendFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_END_DEADLINE_REGISTRATION_FAILED.name(),
                GameStartConstants.GAME_START_FAILED_ACTION
        );
        inOrder.verify(gameRttPingTracker).cleanup(GAME_ROOM_ID);
        inOrder.verify(gameRttMeasurementStore).cleanup(GAME_ROOM_ID);
        inOrder.verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
        verify(gameEndScheduleService).cleanupEndDeadline(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processStartedFailure - GAME_START 전송 실패면 등록된 종료 deadline도 제거한다")
    void processStartedFailure_CleanupEndDeadline() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(true);

        // when
        processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_START_MESSAGE_SEND_FAILED,
                true
        );

        // then
        verify(gameEndScheduleService).cleanupEndDeadline(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processStartedFailure - RTT 상태 조회가 실패해도 DB abort와 실패 이벤트를 수행한다")
    void processStartedFailure_RttStateLookupFailed_AbortAndSendFailure() {
        // given
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(true);
        when(gameRoomReadService.getParticipantUserIds(GAME_ROOM_ID)).thenReturn(java.util.List.of(USER_A_ID, USER_B_ID));
        doThrow(new IllegalStateException("redis down"))
                .when(gameRttMeasurementStore)
                .findState(GAME_ROOM_ID);

        // when
        processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_END_DEADLINE_REGISTRATION_FAILED,
                true
        );

        // then
        verify(gameRoomCommandService).abortInProgressRoomIfInProgress(GAME_ROOM_ID);
        verify(matchUserStatusCommandService).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        verify(gameStartFailedWebSocketSender).sendFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_END_DEADLINE_REGISTRATION_FAILED.name(),
                GameStartConstants.GAME_START_FAILED_ACTION
        );
    }

    @Test
    @DisplayName("processStartedFailure - 이미 ABORTED면 상태 저장소 정리를 계속 수행한다")
    void processStartedFailure_AlreadyAborted_Cleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(false);
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.ABORTED);

        // when
        processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.SCENARIO_LOAD_FAILED,
                false
        );

        // then
        verify(matchUserStatusCommandService).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        verify(gameRttMeasurementStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processStartedFailure - abort 대상이 아니면 상태 저장소를 정리하지 않는다")
    void processStartedFailure_NotAbortable_DoNotCleanup() {
        // given
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(false);
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.FINISHED);

        // when
        processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.SCENARIO_LOAD_FAILED,
                false
        );

        // then
        verify(matchUserStatusCommandService, never()).removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);
        verify(gameRttMeasurementStore, never()).cleanup(GAME_ROOM_ID);
        verify(gameStartFailedWebSocketSender, never()).sendFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.SCENARIO_LOAD_FAILED.name(),
                GameStartConstants.GAME_START_FAILED_ACTION
        );
    }

    @Test
    @DisplayName("processStartedFailure - match status 제거 실패해도 실패 이벤트와 cleanup을 계속 수행한다")
    void processStartedFailure_MatchStatusCleanupFailed_ContinueCleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(true);
        doThrow(new IllegalStateException("match status cleanup failed"))
                .when(matchUserStatusCommandService)
                .removeGameStartFailureStatuses(USER_A_ID, USER_B_ID);

        // when & then
        assertThatCode(() -> processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.SCENARIO_LOAD_FAILED,
                false
        )).doesNotThrowAnyException();
        verify(gameStartFailedWebSocketSender).sendFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.SCENARIO_LOAD_FAILED.name(),
                GameStartConstants.GAME_START_FAILED_ACTION
        );
        verify(gameRttMeasurementStore).cleanup(GAME_ROOM_ID);
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processStartedFailure - RTT cleanup 실패해도 waiting cleanup과 deadline cleanup을 계속 수행한다")
    void processStartedFailure_RttCleanupFailed_ContinueCleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(true);
        doThrow(new IllegalStateException("rtt cleanup failed"))
                .when(gameRttMeasurementStore)
                .cleanup(GAME_ROOM_ID);

        // when & then
        assertThatCode(() -> processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_START_MESSAGE_SEND_FAILED,
                true
        )).doesNotThrowAnyException();
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
        verify(gameEndScheduleService).cleanupEndDeadline(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processStartedFailure - waiting cleanup 실패해도 deadline cleanup을 계속 수행한다")
    void processStartedFailure_WaitingCleanupFailed_ContinueDeadlineCleanup() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(true);
        doThrow(new IllegalStateException("waiting cleanup failed"))
                .when(gameWaitingStore)
                .cleanup(GAME_ROOM_ID);

        // when & then
        assertThatCode(() -> processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_START_MESSAGE_SEND_FAILED,
                true
        )).doesNotThrowAnyException();
        verify(gameEndScheduleService).cleanupEndDeadline(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("processStartedFailure - deadline cleanup 실패를 밖으로 전파하지 않는다")
    void processStartedFailure_DeadlineCleanupFailed_DoNotThrow() {
        // given
        when(gameRttMeasurementStore.findState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomCommandService.abortInProgressRoomIfInProgress(GAME_ROOM_ID)).thenReturn(true);
        doThrow(new IllegalStateException("deadline cleanup failed"))
                .when(gameEndScheduleService)
                .cleanupEndDeadline(GAME_ROOM_ID);

        // when & then
        assertThatCode(() -> processor.processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_START_MESSAGE_SEND_FAILED,
                true
        )).doesNotThrowAnyException();
        verify(gameEndScheduleService).cleanupEndDeadline(GAME_ROOM_ID);
    }

    private GameRttState rttState() {
        return new GameRttState(
                GAME_ROOM_ID,
                USER_A_ID,
                USER_B_ID,
                GameRttStatus.PASSED,
                GameRttStatus.PASSED
        );
    }
}
