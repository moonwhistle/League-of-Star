package com.sang.leagueofstar.game.waiting.service;

import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.game.waiting.domain.GameWaitingState;
import com.sang.leagueofstar.game.waiting.pubsub.GameWaitingTimeoutPubSubPublisher;
import com.sang.leagueofstar.game.waiting.repository.GameWaitingStore;
import com.sang.leagueofstar.matching.command.MatchUserStatusCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameWaitingTimeoutProcessorTest {

    private static final Long GAME_ROOM_ID = 100L;

    @InjectMocks
    private GameWaitingTimeoutProcessor processor;

    @Mock
    private GameWaitingStore gameWaitingStore;

    @Mock
    private GameRoomReadService gameRoomReadService;

    @Mock
    private GameRoomCommandService gameRoomCommandService;

    @Mock
    private MatchUserStatusCommandService matchUserStatusCommandService;

    @Mock
    private GameWaitingTimeoutPubSubPublisher timeoutPubSubPublisher;

    @Test
    @DisplayName("waiting HASH가 없으면 timeout index cleanup만 수행한다")
    void processTimeout_WaitingStateNotFound_Cleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.empty());

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeGameWaitingTimeoutStatuses(1L, 2L);
        verify(timeoutPubSubPublisher, never()).publishTimeout(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("양쪽 READY가 완료된 상태이면 cleanup만 수행한다")
    void processTimeout_BothReady_Cleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(true, true)));

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeGameWaitingTimeoutStatuses(1L, 2L);
        verify(timeoutPubSubPublisher, never()).publishTimeout(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("DB gameRoom이 IN_PROGRESS이면 match status 제거 없이 cleanup한다")
    void processTimeout_GameRoomInProgress_CleanupOnly() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(true, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.IN_PROGRESS);

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeGameWaitingTimeoutStatuses(1L, 2L);
        verify(timeoutPubSubPublisher, never()).publishTimeout(GAME_ROOM_ID);
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("READY gameRoom의 양쪽 READY가 미완료이면 gameRoom을 abort하고 cleanup한다")
    void processTimeout_ReadyGameRoomAbortAndCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(true, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(gameRoomCommandService.abortReadyRoomIfReady(GAME_ROOM_ID)).thenReturn(true);

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameRoomCommandService).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService).removeGameWaitingTimeoutStatuses(1L, 2L);
        org.mockito.InOrder inOrder = inOrder(matchUserStatusCommandService, timeoutPubSubPublisher, gameWaitingStore);
        inOrder.verify(matchUserStatusCommandService).removeGameWaitingTimeoutStatuses(1L, 2L);
        inOrder.verify(timeoutPubSubPublisher).publishTimeout(GAME_ROOM_ID);
        inOrder.verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("이미 ABORTED 상태이면 match status 제거를 재시도하고 cleanup한다")
    void processTimeout_AlreadyAborted_RemoveMatchStatusAndCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(false, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.ABORTED);

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameRoomCommandService, never()).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(matchUserStatusCommandService).removeGameWaitingTimeoutStatuses(1L, 2L);
        org.mockito.InOrder inOrder = inOrder(matchUserStatusCommandService, timeoutPubSubPublisher, gameWaitingStore);
        inOrder.verify(matchUserStatusCommandService).removeGameWaitingTimeoutStatuses(1L, 2L);
        inOrder.verify(timeoutPubSubPublisher).publishTimeout(GAME_ROOM_ID);
        inOrder.verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("abort 처리 중 예외가 발생하면 cleanup하지 않고 다음 tick 재시도를 위해 pending을 유지한다")
    void processTimeout_AbortFailure_DoNotCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(false, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        org.mockito.Mockito.doThrow(new IllegalStateException("abort failed"))
                .when(gameRoomCommandService)
                .abortReadyRoomIfReady(GAME_ROOM_ID);

        // when
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.processTimeoutWithLock(GAME_ROOM_ID))
                .isInstanceOf(IllegalStateException.class);

        // then
        verify(matchUserStatusCommandService, never()).removeGameWaitingTimeoutStatuses(1L, 2L);
        verify(timeoutPubSubPublisher, never()).publishTimeout(GAME_ROOM_ID);
        verify(gameWaitingStore, never()).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("match status 제거 중 예외가 발생하면 cleanup하지 않고 다음 tick 재시도를 위해 pending을 유지한다")
    void processTimeout_RemoveMatchStatusFailure_DoNotCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(false, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(gameRoomCommandService.abortReadyRoomIfReady(GAME_ROOM_ID)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("status cleanup failed"))
                .when(matchUserStatusCommandService)
                .removeGameWaitingTimeoutStatuses(1L, 2L);

        // when
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.processTimeoutWithLock(GAME_ROOM_ID))
                .isInstanceOf(IllegalStateException.class);

        // then
        verify(gameRoomCommandService).abortReadyRoomIfReady(GAME_ROOM_ID);
        verify(timeoutPubSubPublisher, never()).publishTimeout(GAME_ROOM_ID);
        verify(gameWaitingStore, never()).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("timeout 이벤트 publish 중 예외가 발생하면 cleanup하지 않고 다음 tick 재시도를 위해 pending을 유지한다")
    void processTimeout_PublishFailure_DoNotCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(false, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(gameRoomCommandService.abortReadyRoomIfReady(GAME_ROOM_ID)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("publish failed"))
                .when(timeoutPubSubPublisher)
                .publishTimeout(GAME_ROOM_ID);

        // when
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.processTimeoutWithLock(GAME_ROOM_ID))
                .isInstanceOf(IllegalStateException.class);

        // then
        verify(matchUserStatusCommandService).removeGameWaitingTimeoutStatuses(1L, 2L);
        verify(gameWaitingStore, never()).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("READY 조회 후 safe abort가 false이면 match status 제거와 cleanup을 하지 않고 재시도한다")
    void processTimeout_ReadyButAbortNoOp_DoNotCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(false, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(gameRoomCommandService.abortReadyRoomIfReady(GAME_ROOM_ID)).thenReturn(false);

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(matchUserStatusCommandService, never()).removeGameWaitingTimeoutStatuses(1L, 2L);
        verify(timeoutPubSubPublisher, never()).publishTimeout(GAME_ROOM_ID);
        verify(gameWaitingStore, never()).cleanup(GAME_ROOM_ID);
    }

    private GameWaitingState waitingState(boolean userAReady, boolean userBReady) {
        return new GameWaitingState(
                GAME_ROOM_ID,
                1L,
                2L,
                userAReady,
                userBReady,
                1_000L,
                31_000L
        );
    }
}
