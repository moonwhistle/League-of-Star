package com.sang.leagueofstar.game.record.service;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameRecordRankSettlementTriggerTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long WINNER_ID = 1L;

    private final GameRecordRankSettlementService gameRecordRankSettlementService =
            mock(GameRecordRankSettlementService.class);
    private final FinishedGameMatchStatusCleanupService finishedGameMatchStatusCleanupService =
            mock(FinishedGameMatchStatusCleanupService.class);
    private final GameRecordRankSettlementTrigger trigger =
            new GameRecordRankSettlementTrigger(
                    gameRecordRankSettlementService,
                    finishedGameMatchStatusCleanupService
            );

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("settleFinishedGameRoomAfterCommit - transaction synchronization이 없으면 즉시 정산 후 cleanup을 요청한다")
    void settleFinishedGameRoomAfterCommit_NoTransaction_SettleImmediately() {
        // given
        GameRoom gameRoom = finishedGameRoom();

        // when
        trigger.settleFinishedGameRoomAfterCommit(gameRoom);

        // then
        var inOrder = inOrder(gameRecordRankSettlementService, finishedGameMatchStatusCleanupService);
        inOrder.verify(gameRecordRankSettlementService).settleFinishedGameRoom(GAME_ROOM_ID);
        inOrder.verify(finishedGameMatchStatusCleanupService).cleanupIfSettled(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("settleFinishedGameRoomAfterCommit - transaction synchronization이 있으면 afterCommit에서 정산 후 cleanup을 요청한다")
    void settleFinishedGameRoomAfterCommit_WithTransactionSynchronization_SettleAfterCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        GameRoom gameRoom = finishedGameRoom();

        // when
        trigger.settleFinishedGameRoomAfterCommit(gameRoom);

        // then
        verifyNoInteractions(gameRecordRankSettlementService, finishedGameMatchStatusCleanupService);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        var inOrder = inOrder(gameRecordRankSettlementService, finishedGameMatchStatusCleanupService);
        inOrder.verify(gameRecordRankSettlementService).settleFinishedGameRoom(GAME_ROOM_ID);
        inOrder.verify(finishedGameMatchStatusCleanupService).cleanupIfSettled(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("settleFinishedGameRoomAfterCommit - 정산 실패는 호출자에게 전파하지 않는다")
    void settleFinishedGameRoomAfterCommit_SettlementFailed_SwallowException() {
        // given
        GameRoom gameRoom = finishedGameRoom();
        doThrow(new RuntimeException("failed"))
                .when(gameRecordRankSettlementService)
                .settleFinishedGameRoom(GAME_ROOM_ID);
        when(gameRecordRankSettlementService.countRecordsByGameRoomId(GAME_ROOM_ID)).thenReturn(0L);

        // when
        trigger.settleFinishedGameRoomAfterCommit(gameRoom);

        // then
        verify(gameRecordRankSettlementService).settleFinishedGameRoom(GAME_ROOM_ID);
        verify(gameRecordRankSettlementService).countRecordsByGameRoomId(GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService, never()).cleanupIfSettled(GAME_ROOM_ID);
    }

    private GameRoom finishedGameRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .build();
        gameRoom.finish(GameResult.PLAYER1_WIN, WINNER_ID);
        return gameRoom;
    }
}
