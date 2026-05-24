package com.sang.smite.domain.record.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameParticipantResult;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import com.sang.smite.domain.game.service.GameRoomParticipantResult;
import com.sang.smite.domain.game.service.GameRoomResultResolver;
import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.vo.SeriesStatus;
import com.sang.smite.domain.rank.domain.vo.SeriesType;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementCommand;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementResult;
import com.sang.smite.domain.rank.repository.RankSeriesRepository;
import com.sang.smite.domain.rank.service.RankCommandService;
import com.sang.smite.domain.record.domain.GameRecord;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.smite.domain.record.repository.GameRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GameRecordRankSettlementService {

    private final GameRoomRepository gameRoomRepository;
    private final GameRecordRepository gameRecordRepository;
    private final RankCommandService rankCommandService;
    private final RankSeriesRepository rankSeriesRepository;
    private final GameRoomResultResolver gameRoomResultResolver;

    /**
     * 이미 FINISHED로 확정된 gameRoom을 기준으로 record/rank 정산을 수행합니다.
     *
     * <p>gameRoom 종료 transaction과 분리된 별도 transaction 경계입니다.</p>
     */
    public void settleFinishedGameRoom(Long gameRoomId) {
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
        long recordCount = gameRecordRepository.countByGameRoomId(gameRoomId);
        if (recordCount == GameRoom.MAX_PARTICIPANTS) {
            return;
        }
        if (recordCount != 0) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }

        List<GameRoomParticipantResult> participantResults = gameRoomResultResolver.resolveParticipantResults(gameRoom);
        List<RecordSettlementTarget> targets = participantResults.stream()
                .map(this::resolveRecordSettlementTarget)
                .toList();
        Map<Long, RankRecordSettlementResult> rankResults = rankCommandService.applyRecordResults(
                        targets.stream()
                                .map(RecordSettlementTarget::toRankCommand)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(RankRecordSettlementResult::userId, Function.identity()));

        List<GameRecord> records = targets.stream()
                .map(target -> createRecord(gameRoomId, target, rankResults.get(target.userId())))
                .toList();

        gameRecordRepository.saveAll(records);
    }

    private RecordSettlementTarget resolveRecordSettlementTarget(GameRoomParticipantResult participantResult) {
        Optional<RankSeries> rankSeries = rankSeriesRepository.findByUserIdAndStatus(
                participantResult.userId(),
                SeriesStatus.IN_PROGRESS
        );
        return new RecordSettlementTarget(
                participantResult.userId(),
                participantResult.opponentId(),
                rankSeries.map(RankSeries::getId).orElse(null),
                resolveSeriesType(rankSeries),
                toRecordResult(participantResult.result())
        );
    }

    private GameRecord createRecord(
            Long gameRoomId,
            RecordSettlementTarget target,
            RankRecordSettlementResult rankResult
    ) {
        if (rankResult == null) {
            throw new CoreException(CoreErrorCode.RANK_NOT_FOUND);
        }
        return GameRecord.create(
                gameRoomId,
                target.userId(),
                target.opponentId(),
                target.rankSeriesId(),
                target.seriesType(),
                target.result(),
                rankResult.lpBefore(),
                rankResult.lpAfter(),
                rankResult.rankBefore(),
                rankResult.rankAfter()
        );
    }

    private GameRecordSeriesType resolveSeriesType(Optional<RankSeries> rankSeries) {
        return rankSeries
                .map(RankSeries::getType)
                .map(this::toRecordSeriesType)
                .orElse(GameRecordSeriesType.RANK);
    }

    private GameRecordSeriesType toRecordSeriesType(SeriesType seriesType) {
        return switch (seriesType) {
            case PLACEMENT -> GameRecordSeriesType.PLACEMENT;
            case PROMOTION -> GameRecordSeriesType.PROMOTION;
        };
    }

    private GameRecordResult toRecordResult(GameParticipantResult participantResult) {
        return switch (participantResult) {
            case WIN -> GameRecordResult.WIN;
            case LOSS -> GameRecordResult.LOSS;
            case DRAW -> GameRecordResult.DRAW;
        };
    }

    private record RecordSettlementTarget(
            Long userId,
            Long opponentId,
            Long rankSeriesId,
            GameRecordSeriesType seriesType,
            GameRecordResult result
    ) {
        private RankRecordSettlementCommand toRankCommand() {
            return new RankRecordSettlementCommand(userId, opponentId, result, seriesType);
        }
    }
}
