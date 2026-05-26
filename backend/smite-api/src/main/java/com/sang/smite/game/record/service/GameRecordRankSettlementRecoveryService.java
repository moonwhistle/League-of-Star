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
        if (recordCount != GameRecordConstants.SETTLED_RECORD_COUNT) {
            log.warn("Incomplete game record/rank settlement detected: gameRoomId={}, recordCount={}",
                    gameRoomId, recordCount);
        }
    }

    private void settleSafely(Long gameRoomId) {
        try {
            gameRecordRankSettlementService.settleFinishedGameRoom(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to recover game record/rank settlement: gameRoomId={}", gameRoomId, e);
        }
    }
}
