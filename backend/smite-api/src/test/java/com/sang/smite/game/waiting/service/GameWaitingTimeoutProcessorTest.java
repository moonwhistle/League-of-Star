package com.sang.smite.game.waiting.service;

import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.waiting.domain.GameWaitingState;
import com.sang.smite.game.waiting.repository.GameWaitingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

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

    @Test
    @DisplayName("waiting HASH가 없으면 timeout index cleanup만 수행한다")
    void processTimeout_WaitingStateNotFound_Cleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.empty());

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
        verify(gameRoomReadService, never()).getStatus(GAME_ROOM_ID);
        verify(gameRoomCommandService, never()).abortReadyRoom(GAME_ROOM_ID);
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
        verify(gameRoomReadService, never()).getStatus(GAME_ROOM_ID);
        verify(gameRoomCommandService, never()).abortReadyRoom(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("DB gameRoom 상태가 READY가 아니면 cleanup만 수행한다")
    void processTimeout_GameRoomNotReady_Cleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(true, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.IN_PROGRESS);

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameRoomCommandService, never()).abortReadyRoom(GAME_ROOM_ID);
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("READY gameRoom의 양쪽 READY가 미완료이면 gameRoom을 abort하고 cleanup한다")
    void processTimeout_ReadyGameRoomAbortAndCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(true, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);

        // when
        processor.processTimeoutWithLock(GAME_ROOM_ID);

        // then
        verify(gameRoomCommandService).abortReadyRoom(GAME_ROOM_ID);
        verify(gameWaitingStore).cleanup(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("abort 처리 중 예외가 발생하면 cleanup하지 않고 다음 tick 재시도를 위해 pending을 유지한다")
    void processTimeout_AbortFailure_DoNotCleanup() {
        // given
        when(gameWaitingStore.findWaitingState(GAME_ROOM_ID)).thenReturn(Optional.of(waitingState(false, false)));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        org.mockito.Mockito.doThrow(new IllegalStateException("abort failed"))
                .when(gameRoomCommandService)
                .abortReadyRoom(GAME_ROOM_ID);

        // when
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.processTimeoutWithLock(GAME_ROOM_ID))
                .isInstanceOf(IllegalStateException.class);

        // then
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
