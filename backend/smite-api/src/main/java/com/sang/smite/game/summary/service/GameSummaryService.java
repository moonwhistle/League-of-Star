package com.sang.smite.game.summary.service;

import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.domain.game.service.dto.GameRoomSummaryReadModel;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.record.domain.GameRecord;
import com.sang.smite.domain.record.service.GameRecordReadService;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.service.UserReadService;
import com.sang.smite.game.summary.dto.GameSummaryDoneResponse;
import com.sang.smite.game.summary.dto.GameSummaryPendingResponse;
import com.sang.smite.game.summary.dto.GameSummaryPlayerResponse;
import com.sang.smite.game.summary.dto.GameSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSummaryService {

    private static final long PENDING_RETRY_AFTER_MILLIS = 1_000L;
    private static final int SETTLED_RECORD_COUNT = 2;

    private final GameRoomReadService gameRoomReadService;
    private final GameRecordReadService gameRecordReadService;
    private final UserReadService userReadService;

    public GameSummaryResponse getSummary(Long gameId, Long requestUserId) {
        GameRoomSummaryReadModel gameRoom = gameRoomReadService.getSummaryReadModel(gameId);
        validateParticipant(gameRoom, requestUserId);
        validateFinished(gameRoom);

        long recordCount = gameRecordReadService.countByGameRoomId(gameId);
        if (recordCount == 0L) {
            return pending(gameId);
        }
        if (recordCount == 1L) {
            log.warn("Incomplete game summary record state: gameId={}, recordCount={}", gameId, recordCount);
            return pending(gameId);
        }
        if (recordCount != SETTLED_RECORD_COUNT) {
            throwInvalidRecordState();
        }

        return done(gameRoom, requestUserId);
    }

    private void validateParticipant(GameRoomSummaryReadModel gameRoom, Long requestUserId) {
        if (gameRoom.participantUserIds().size() != SETTLED_RECORD_COUNT) {
            throwInvalidRecordState();
        }
        if (gameRoom.participantUserIds().stream().distinct().count() != SETTLED_RECORD_COUNT) {
            throwInvalidRecordState();
        }
        if (!gameRoom.participantUserIds().contains(requestUserId)) {
            throw new ApiException(ApiErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateFinished(GameRoomSummaryReadModel gameRoom) {
        if (!gameRoom.status().isFinished()) {
            throw new ApiException(ApiErrorCode.GAME_SUMMARY_NOT_FINISHED);
        }
        if (gameRoom.result() == null) {
            throwInvalidRecordState();
        }
        if (gameRoom.result() == GameResult.DRAW && gameRoom.winnerId() != null) {
            throwInvalidRecordState();
        }
        if (gameRoom.result() != GameResult.DRAW && gameRoom.winnerId() == null) {
            throwInvalidRecordState();
        }
    }

    private GameSummaryPendingResponse pending(Long gameId) {
        return GameSummaryPendingResponse.of(gameId, PENDING_RETRY_AFTER_MILLIS);
    }

    private GameSummaryDoneResponse done(GameRoomSummaryReadModel gameRoom, Long requestUserId) {
        List<GameRecord> records = gameRecordReadService.findByGameRoomId(gameRoom.gameRoomId());
        validateRecords(gameRoom, records);

        Map<Long, User> usersById = userReadService.findByIds(gameRoom.participantUserIds()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        validateUsers(gameRoom, usersById);

        GameRecord myRecord = findRecordByUserId(records, requestUserId);
        GameRecord opponentRecord = records.stream()
                .filter(record -> !Objects.equals(record.getUserId(), requestUserId))
                .findFirst()
                .orElseThrow(GameSummaryService::invalidRecordState);

        return GameSummaryDoneResponse.of(
                gameRoom.gameRoomId(),
                gameRoom.result(),
                winnerUserId(gameRoom),
                gameRoom.finishedAt(),
                toPlayerResponse(myRecord, usersById.get(myRecord.getUserId())),
                toPlayerResponse(opponentRecord, usersById.get(opponentRecord.getUserId()))
        );
    }

    private void validateRecords(GameRoomSummaryReadModel gameRoom, List<GameRecord> records) {
        if (records.size() != SETTLED_RECORD_COUNT) {
            throwInvalidRecordState();
        }

        List<Long> recordUserIds = records.stream()
                .map(GameRecord::getUserId)
                .toList();
        if (recordUserIds.stream().distinct().count() != SETTLED_RECORD_COUNT) {
            throwInvalidRecordState();
        }
        if (!recordUserIds.containsAll(gameRoom.participantUserIds())
                || !gameRoom.participantUserIds().containsAll(recordUserIds)) {
            throwInvalidRecordState();
        }
    }

    private void validateUsers(GameRoomSummaryReadModel gameRoom, Map<Long, User> usersById) {
        if (!usersById.keySet().containsAll(gameRoom.participantUserIds())) {
            throw new CoreException(CoreErrorCode.USER_NOT_FOUND);
        }
    }

    private GameRecord findRecordByUserId(List<GameRecord> records, Long userId) {
        return records.stream()
                .filter(record -> Objects.equals(record.getUserId(), userId))
                .findFirst()
                .orElseThrow(GameSummaryService::invalidRecordState);
    }

    private Long winnerUserId(GameRoomSummaryReadModel gameRoom) {
        if (gameRoom.result() == GameResult.DRAW) {
            return null;
        }
        return gameRoom.winnerId();
    }

    private GameSummaryPlayerResponse toPlayerResponse(GameRecord record, User user) {
        return new GameSummaryPlayerResponse(
                record.getUserId(),
                user.getNickname(),
                record.getResult(),
                record.getLpBefore(),
                record.getLpAfter(),
                record.getLpChange(),
                rankName(record.getRankBefore()),
                rankName(record.getRankAfter()),
                record.getSeriesType(),
                record.getRankSeriesId()
        );
    }

    private String rankName(Rank rank) {
        if (rank == null || rank.tier() == null) {
            throwInvalidRecordState();
        }
        if (rank.division() == null) {
            return rank.tier().name();
        }
        return rank.tier().name() + "_" + rank.division().name();
    }

    private static ApiException invalidRecordState() {
        return new ApiException(ApiErrorCode.GAME_SUMMARY_INVALID_RECORD_STATE);
    }

    private static void throwInvalidRecordState() {
        throw invalidRecordState();
    }
}
