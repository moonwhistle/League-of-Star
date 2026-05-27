package com.sang.smite.game.record.service;

import com.sang.smite.domain.record.service.GameRecordRankSettlementService;
import com.sang.smite.game.record.common.constant.GameRecordConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecordRankSettlementRecoveryService {

    private final GameRecordRankSettlementService gameRecordRankSettlementService;
    private final FinishedGameMatchStatusCleanupService finishedGameMatchStatusCleanupService;

    public void recoverUnsettledFinishedGameRooms() {
        List<Long> gameRoomIds = gameRecordRankSettlementService.findUnsettledFinishedGameRoomIds(
                GameRecordConstants.RECORD_RECOVERY_CANDIDATE_BATCH_SIZE
        );
        gameRoomIds.forEach(this::recoverGameRoom);
    }

    private void recoverGameRoom(Long gameRoomId) {
        long recordCount = gameRecordRankSettlementService.countRecordsByGameRoomId(gameRoomId);
        if (recordCount == 0) {
            settleSafely(gameRoomId);
            return;
        }
        if (recordCount == GameRecordConstants.SETTLED_RECORD_COUNT) {
            cleanupSafely(gameRoomId);
            return;
        }
        if (recordCount != GameRecordConstants.SETTLED_RECORD_COUNT) {
            log.warn("Incomplete game record/rank settlement detected: gameRoomId={}, recordCount={}",
                    gameRoomId, recordCount);
        }
    }

    /**
     * 복구 정산 실패는 다음 후보 처리를 막지 않도록 로그만 남깁니다.
     */
    private void settleSafely(Long gameRoomId) {
        try {
            gameRecordRankSettlementService.settleFinishedGameRoom(gameRoomId);
            cleanupSafely(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to recover game record/rank settlement: gameRoomId={}", gameRoomId, e);
        }
    }

    /**
     * match status cleanup 실패는 다음 recovery 후보 처리를 막지 않도록 로그만 남깁니다.
     */
    private void cleanupSafely(Long gameRoomId) {
        try {
            finishedGameMatchStatusCleanupService.cleanupIfSettled(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to recover finished game match status cleanup: gameRoomId={}", gameRoomId, e);
        }
    }
}
