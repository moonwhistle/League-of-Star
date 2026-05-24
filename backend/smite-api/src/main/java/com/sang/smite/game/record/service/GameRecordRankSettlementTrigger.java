package com.sang.smite.game.record.service;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.record.service.GameRecordRankSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecordRankSettlementTrigger {

    private final GameRecordRankSettlementService gameRecordRankSettlementService;

    /**
     * gameRoom 종료 transaction commit 이후 record/rank 정산을 요청합니다.
     *
     * <p>정산 실패는 종료 결과와 GAME_RESULT 전송 흐름에 전파하지 않습니다.</p>
     */
    public void settleFinishedGameRoomAfterCommit(GameRoom gameRoom) {
        GameRecordRankSettlementCommand command = GameRecordRankSettlementCommand.from(gameRoom);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    settleSafely(command);
                }
            });
            return;
        }

        settleSafely(command);
    }

    private void settleSafely(GameRecordRankSettlementCommand command) {
        try {
            gameRecordRankSettlementService.settleFinishedGameRoom(command.gameRoomId());
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to settle game record/rank: gameRoomId={}, result={}, winnerId={}",
                    command.gameRoomId(),
                    command.result(),
                    command.winnerId(),
                    e
            );
        }
    }
}
