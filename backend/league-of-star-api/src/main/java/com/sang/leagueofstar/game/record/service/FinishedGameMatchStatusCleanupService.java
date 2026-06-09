package com.sang.leagueofstar.game.record.service;

import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import com.sang.leagueofstar.matching.command.MatchUserStatusCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinishedGameMatchStatusCleanupService {

    private final GameRoomReadService gameRoomReadService;
    private final GameRecordRankSettlementService gameRecordRankSettlementService;
    private final MatchUserStatusCommandService matchUserStatusCommandService;

    public void cleanupIfSettled(Long gameRoomId) {
        try {
            cleanup(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup finished game match status: gameRoomId={}", gameRoomId, e);
        }
    }

    private void cleanup(Long gameRoomId) {
        GameStatus status = gameRoomReadService.getStatus(gameRoomId);
        if (!status.isFinished()) {
            log.warn("Skipped match status cleanup because gameRoom is not FINISHED: gameRoomId={}, status={}",
                    gameRoomId, status);
            return;
        }

        long recordCount = gameRecordRankSettlementService.countRecordsByGameRoomId(gameRoomId);
        if (recordCount == 0) {
            return;
        }
        if (recordCount != GameRecordConstants.SETTLED_RECORD_COUNT) {
            log.warn("Skipped match status cleanup because record/rank settlement is incomplete: "
                            + "gameRoomId={}, recordCount={}",
                    gameRoomId, recordCount);
            return;
        }

        List<Long> participantUserIds = gameRoomReadService.getParticipantUserIds(gameRoomId);
        if (participantUserIds.size() != GameRecordConstants.SETTLED_RECORD_COUNT) {
            log.warn("Skipped match status cleanup because participant count is invalid: "
                            + "gameRoomId={}, participantUserIds={}, recordCount={}",
                    gameRoomId, participantUserIds, recordCount);
            return;
        }

        cleanupMatchStatuses(gameRoomId, participantUserIds, recordCount);
    }

    private void cleanupMatchStatuses(Long gameRoomId, List<Long> participantUserIds, long recordCount) {
        try {
            matchUserStatusCommandService.removeFinishedGameStatuses(
                    participantUserIds.get(0),
                    participantUserIds.get(1)
            );
        } catch (RuntimeException e) {
            log.warn("Failed to remove finished game match statuses: gameRoomId={}, participantUserIds={}, "
                            + "recordCount={}",
                    gameRoomId, participantUserIds, recordCount, e);
        }
    }
}
