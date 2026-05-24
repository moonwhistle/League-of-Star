package com.sang.smite.game.record.service;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.record.service.GameRecordRankSettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GameRecordRankSettlementTriggerTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long WINNER_ID = 1L;

    private final GameRecordRankSettlementService gameRecordRankSettlementService =
            mock(GameRecordRankSettlementService.class);
    private final GameRecordRankSettlementTrigger trigger =
            new GameRecordRankSettlementTrigger(gameRecordRankSettlementService);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("settleFinishedGameRoomAfterCommit - transaction synchronization이 없으면 즉시 정산을 요청한다")
    void settleFinishedGameRoomAfterCommit_NoTransaction_SettleImmediately() {
        // given
        GameRoom gameRoom = finishedGameRoom();

        // when
        trigger.settleFinishedGameRoomAfterCommit(gameRoom);

        // then
        verify(gameRecordRankSettlementService).settleFinishedGameRoom(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("settleFinishedGameRoomAfterCommit - transaction synchronization이 있으면 afterCommit에서 정산을 요청한다")
    void settleFinishedGameRoomAfterCommit_WithTransactionSynchronization_SettleAfterCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        GameRoom gameRoom = finishedGameRoom();

        // when
        trigger.settleFinishedGameRoomAfterCommit(gameRoom);

        // then
        verifyNoInteractions(gameRecordRankSettlementService);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(gameRecordRankSettlementService).settleFinishedGameRoom(GAME_ROOM_ID);
    }

    @Test
    @DisplayName("settleFinishedGameRoomAfterCommit - 정산 실패는 호출자에게 전파하지 않는다")
    void settleFinishedGameRoomAfterCommit_SettlementFailed_SwallowException() {
        // given
        GameRoom gameRoom = finishedGameRoom();
        doThrow(new RuntimeException("failed"))
                .when(gameRecordRankSettlementService)
                .settleFinishedGameRoom(GAME_ROOM_ID);

        // when
        trigger.settleFinishedGameRoomAfterCommit(gameRoom);

        // then
        verify(gameRecordRankSettlementService).settleFinishedGameRoom(GAME_ROOM_ID);
    }

    private GameRoom finishedGameRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .build();
        gameRoom.finish(GameResult.PLAYER1_WIN, WINNER_ID);
        return gameRoom;
    }
}
