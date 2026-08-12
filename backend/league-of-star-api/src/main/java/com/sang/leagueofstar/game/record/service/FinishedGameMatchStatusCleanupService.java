package com.sang.leagueofstar.game.record.service;

import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import com.sang.leagueofstar.matching.command.MatchUserStatusCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FinishedGameMatchStatusCleanupService {

    private final GameRoomReadService gameRoomReadService;
    private final GameRecordRankSettlementService gameRecordRankSettlementService;
    private final MatchUserStatusCommandService matchUserStatusCommandService;

    public void cleanupSettledGame(Long gameRoomId) {
        GameMode gameMode = gameRoomReadService.getMode(gameRoomId);
        if (!gameMode.isMatch()) {
            return;
        }

        GameStatus status = gameRoomReadService.getStatus(gameRoomId);
        if (!status.isFinished()) {
            throw new IllegalStateException(
                    "Cannot cleanup match status before game finish: gameRoomId=" + gameRoomId + ", status=" + status
            );
        }

        long recordCount = gameRecordRankSettlementService.countRecordsByGameRoomId(gameRoomId);
        if (recordCount != GameRecordConstants.SETTLED_RECORD_COUNT) {
            throw new IllegalStateException(
                    "Cannot cleanup match status before settlement: gameRoomId=" + gameRoomId
                            + ", recordCount=" + recordCount
            );
        }

        List<Long> participantUserIds = gameRoomReadService.getParticipantUserIds(gameRoomId);
        if (participantUserIds.size() != GameRecordConstants.SETTLED_RECORD_COUNT) {
            throw new IllegalStateException(
                    "Cannot cleanup match status with invalid participants: gameRoomId=" + gameRoomId
                            + ", participantUserIds=" + participantUserIds
            );
        }

        matchUserStatusCommandService.removeFinishedGameStatuses(
                participantUserIds.get(0),
                participantUserIds.get(1)
        );
    }
}
