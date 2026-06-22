package com.sang.leagueofstar.domain.record.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameParticipantResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.GameRoomResultResolver;
import com.sang.leagueofstar.domain.game.service.dto.GameRoomParticipantResult;
import com.sang.leagueofstar.domain.rank.service.dto.RankRecordSettlementCommand;
import com.sang.leagueofstar.domain.rank.service.dto.RankRecordSettlementResult;
import com.sang.leagueofstar.domain.rank.service.RankCommandService;
import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.domain.record.repository.GameRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GameRecordRankSettlementService {

    private static final long SETTLED_RECORD_COUNT = GameRoom.MAX_PARTICIPANTS;

    private final GameRoomRepository gameRoomRepository;
    private final GameRecordRepository gameRecordRepository;
    private final RankCommandService rankCommandService;
    private final GameRoomResultResolver gameRoomResultResolver;

    /**
     * 이미 FINISHED로 확정된 gameRoom을 기준으로 record/rank 정산을 수행합니다.
     *
     * <p>gameRoom 종료 transaction과 분리된 별도 transaction 경계입니다.</p>
     */
    public void settleFinishedGameRoom(Long gameRoomId) {
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
        if (gameRoom.isPracticeMode()) {
            return;
        }
        long recordCount = gameRecordRepository.countByGameRoomId(gameRoomId);
        if (recordCount == SETTLED_RECORD_COUNT) {
            return;
        }
        if (recordCount != 0) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }

        List<GameRoomParticipantResult> participantResults = gameRoomResultResolver.resolveParticipantResults(gameRoom);
        Map<Long, RankRecordSettlementResult> rankResults = rankCommandService.applyRecordResults(
                        participantResults.stream()
                                .map(this::toRankCommand)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(RankRecordSettlementResult::userId, Function.identity()));

        List<GameRecord> records = participantResults.stream()
                .map(participantResult -> createRecord(
                        gameRoomId,
                        participantResult,
                        rankResults.get(participantResult.userId())
                ))
                .toList();

        gameRecordRepository.saveAll(records);
    }

    /**
     * gameRoom 기준 생성된 record 수를 조회합니다.
     */
    @Transactional(readOnly = true)
    public long countRecordsByGameRoomId(Long gameRoomId) {
        return gameRecordRepository.countByGameRoomId(gameRoomId);
    }

    /**
     * FINISHED 상태지만 참가자 수만큼 record가 생성되지 않은 gameRoom 후보를 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<Long> findUnsettledFinishedGameRoomIds(int limit) {
        return gameRoomRepository.findGameRoomIdsByStatusAndGameModeAndRecordCountNot(
                GameStatus.FINISHED,
                GameMode.MATCH,
                SETTLED_RECORD_COUNT,
                PageRequest.of(0, limit)
        );
    }

    private RankRecordSettlementCommand toRankCommand(GameRoomParticipantResult participantResult) {
        return new RankRecordSettlementCommand(
                participantResult.userId(),
                participantResult.opponentId(),
                toRecordResult(participantResult.result())
        );
    }

    private GameRecord createRecord(
            Long gameRoomId,
            GameRoomParticipantResult participantResult,
            RankRecordSettlementResult rankResult
    ) {
        if (rankResult == null) {
            throw new CoreException(CoreErrorCode.RANK_NOT_FOUND);
        }
        return GameRecord.create(
                gameRoomId,
                participantResult.userId(),
                participantResult.opponentId(),
                rankResult.rankSeriesId(),
                rankResult.seriesType(),
                toRecordResult(participantResult.result()),
                rankResult.lpBefore(),
                rankResult.lpAfter(),
                rankResult.rankBefore(),
                rankResult.rankAfter()
        );
    }

    private GameRecordResult toRecordResult(GameParticipantResult participantResult) {
        return switch (participantResult) {
            case WIN -> GameRecordResult.WIN;
            case LOSS -> GameRecordResult.LOSS;
            case DRAW -> GameRecordResult.DRAW;
        };
    }
}
