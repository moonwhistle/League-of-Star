package com.sang.smite.domain.record.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import com.sang.smite.domain.game.service.GameRoomResultResolver;
import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.SeriesStatus;
import com.sang.smite.domain.rank.domain.vo.SeriesType;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementCommand;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementResult;
import com.sang.smite.domain.rank.repository.RankSeriesRepository;
import com.sang.smite.domain.rank.service.RankCommandService;
import com.sang.smite.domain.record.domain.GameRecord;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.smite.domain.record.repository.GameRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRecordRankSettlementServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Mock
    private GameRecordRepository gameRecordRepository;

    @Mock
    private RankCommandService rankCommandService;

    @Mock
    private RankSeriesRepository rankSeriesRepository;

    @Spy
    private GameRoomResultResolver gameRoomResultResolver;

    @InjectMocks
    private GameRecordRankSettlementService gameRecordRankSettlementService;

    @Test
    @DisplayName("settleFinishedGameRoom - record가 없으면 참가자별 gameRecord 2행을 생성한다")
    void settleFinishedGameRoom_CreateRecords() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
        given(rankSeriesRepository.findByUserIdAndStatus(FIRST_USER_ID, SeriesStatus.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(rankSeriesRepository.findByUserIdAndStatus(SECOND_USER_ID, SeriesStatus.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(rankCommandService.applyRecordResults(anyList())).willReturn(List.of(
                rankResult(FIRST_USER_ID, 20, 45),
                rankResult(SECOND_USER_ID, 30, 5)
        ));

        // when
        gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID);

        // then
        ArgumentCaptor<List<GameRecord>> recordsCaptor = gameRecordListCaptor();
        verify(gameRecordRepository).saveAll(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue())
                .extracting(
                        GameRecord::getGameRoomId,
                        GameRecord::getUserId,
                        GameRecord::getOpponentId,
                        GameRecord::getResult,
                        GameRecord::getLpBefore,
                        GameRecord::getLpAfter,
                        GameRecord::getLpChange,
                        GameRecord::getSeriesType,
                        GameRecord::getRankSeriesId
                )
                .containsExactly(
                        tuple(GAME_ROOM_ID, FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.WIN,
                        20, 45, 25, GameRecordSeriesType.RANK, null),
                        tuple(GAME_ROOM_ID, SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.LOSS,
                                30, 5, -25, GameRecordSeriesType.RANK, null)
                );
        ArgumentCaptor<List<RankRecordSettlementCommand>> commandCaptor = rankCommandListCaptor();
        verify(rankCommandService).applyRecordResults(commandCaptor.capture());
        assertThat(commandCaptor.getValue())
                .extracting(
                        RankRecordSettlementCommand::userId,
                        RankRecordSettlementCommand::opponentId,
                        RankRecordSettlementCommand::result,
                        RankRecordSettlementCommand::seriesType
                )
                .containsExactly(
                        tuple(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.WIN, GameRecordSeriesType.RANK),
                        tuple(SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.LOSS, GameRecordSeriesType.RANK)
                );
    }

    @Test
    @DisplayName("settleFinishedGameRoom - 진행 중인 RankSeries가 있으면 rankSeriesId와 seriesType을 저장한다")
    void settleFinishedGameRoom_WithRankSeries() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.DRAW, null);
        RankSeries placement = RankSeries.builder()
                .id(10L)
                .userId(FIRST_USER_ID)
                .type(SeriesType.PLACEMENT)
                .totalGamesRequired(10)
                .build();
        RankSeries promotion = RankSeries.builder()
                .id(20L)
                .userId(SECOND_USER_ID)
                .type(SeriesType.PROMOTION)
                .targetRank(Rank.of(Tier.BRONZE, Division.IV))
                .totalGamesRequired(3)
                .build();
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
        given(rankSeriesRepository.findByUserIdAndStatus(FIRST_USER_ID, SeriesStatus.IN_PROGRESS))
                .willReturn(Optional.of(placement));
        given(rankSeriesRepository.findByUserIdAndStatus(SECOND_USER_ID, SeriesStatus.IN_PROGRESS))
                .willReturn(Optional.of(promotion));
        given(rankCommandService.applyRecordResults(anyList())).willReturn(List.of(
                rankResult(FIRST_USER_ID, 20, 20),
                rankResult(SECOND_USER_ID, 30, 30)
        ));

        // when
        gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID);

        // then
        ArgumentCaptor<List<GameRecord>> recordsCaptor = gameRecordListCaptor();
        verify(gameRecordRepository).saveAll(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue())
                .extracting(GameRecord::getUserId, GameRecord::getRankSeriesId, GameRecord::getSeriesType)
                .containsExactly(
                        tuple(FIRST_USER_ID, 10L, GameRecordSeriesType.PLACEMENT),
                        tuple(SECOND_USER_ID, 20L, GameRecordSeriesType.PROMOTION)
                );
    }

    @Test
    @DisplayName("settleFinishedGameRoom - 이미 record 2행이 있으면 no-op 처리한다")
    void settleFinishedGameRoom_AlreadySettled_NoOp() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(2L);

        // when
        gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID);

        // then
        verify(gameRecordRepository, never()).saveAll(anyList());
        verify(rankCommandService, never()).applyRecordResults(anyList());
    }

    @Test
    @DisplayName("settleFinishedGameRoom - record가 1행이면 불완전 정산 상태로 보고 예외를 던진다")
    void settleFinishedGameRoom_PartiallySettled_ThrowException() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(1L);

        // when & then
        assertThatThrownBy(() -> gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
        verify(gameRecordRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("settleFinishedGameRoom - FINISHED가 아니면 record를 생성하지 않고 예외를 던진다")
    void settleFinishedGameRoom_NotFinished_ThrowException() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);

        // when & then
        assertThatThrownBy(() -> gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
        verify(gameRecordRepository, never()).saveAll(anyList());
    }

    private GameRoom createFinishedGameRoom(GameResult result, Long winnerId) {
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.finish(result, winnerId);
        return gameRoom;
    }

    private GameRoom createReadyGameRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        return gameRoom;
    }

    private RankRecordSettlementResult rankResult(Long userId, int lpBefore, int lpAfter) {
        return new RankRecordSettlementResult(
                userId,
                lpBefore,
                lpAfter,
                Rank.of(Tier.IRON, Division.IV),
                Rank.of(Tier.IRON, Division.IV)
        );
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<GameRecord>> gameRecordListCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<RankRecordSettlementCommand>> rankCommandListCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
