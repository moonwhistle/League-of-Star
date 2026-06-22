package com.sang.leagueofstar.game.record.service;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
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
    private final FinishedGameMatchStatusCleanupService finishedGameMatchStatusCleanupService;

    /**
     * gameRoom 종료 transaction commit 이후 record/rank 정산을 요청합니다.
     *
     * <p>정산 실패는 종료 결과와 GAME_RESULT 전송 흐름에 전파하지 않습니다.</p>
     */
    public void settleFinishedGameRoomAfterCommit(GameRoom gameRoom) {
        if (gameRoom.isPracticeMode()) {
            return;
        }
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

    /**
     * record/rank 정산 실패는 이미 확정된 gameRoom 결과와 GAME_RESULT 흐름을 되돌리지 않습니다.
     *
     * <p>CoreException을 포함한 RuntimeException은 로그로 남기고,
     * FINISHED + record count != 2 복구 scheduler가 재시도하도록 둡니다.</p>
     */
    private void settleSafely(GameRecordRankSettlementCommand command) {
        try {
            gameRecordRankSettlementService.settleFinishedGameRoom(command.gameRoomId());
            finishedGameMatchStatusCleanupService.cleanupIfSettled(command.gameRoomId());
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to settle game record/rank: gameRoomId={}, result={}, winnerId={}, recordCount={}",
                    command.gameRoomId(),
                    command.result(),
                    command.winnerId(),
                    countRecordsSafely(command.gameRoomId()),
                    e
            );
        }
    }

    private Long countRecordsSafely(Long gameRoomId) {
        try {
            return gameRecordRankSettlementService.countRecordsByGameRoomId(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to count game records after settlement failure: gameRoomId={}", gameRoomId, e);
            return null;
        }
    }
}
