package com.sang.leagueofstar.domain.record.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.GameRoomResultResolver;
import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.rank.service.dto.RankRecordSettlementCommand;
import com.sang.leagueofstar.domain.rank.service.dto.RankRecordSettlementResult;
import com.sang.leagueofstar.domain.rank.service.RankCommandService;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.leagueofstar.domain.record.repository.GameRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
    private RankReadService rankReadService;

    @Spy
    private GameRoomResultResolver gameRoomResultResolver;

    @InjectMocks
    private GameRecordRankSettlementService gameRecordRankSettlementService;

    @Test
    @DisplayName("settleFinishedGameRoom - afterCommit 호출에서도 pessimistic lock이 가능하도록 새 transaction을 연다")
    void settleFinishedGameRoom_RequiresNewTransaction() throws NoSuchMethodException {
        // when
        Method method = GameRecordRankSettlementService.class.getMethod("settleFinishedGameRoom", Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("settleFinishedGameRoom - record가 없으면 참가자별 gameRecord 2행을 생성한다")
    void settleFinishedGameRoom_CreateRecords() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
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
                        RankRecordSettlementCommand::result
                )
                .containsExactly(
                        tuple(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.WIN),
                        tuple(SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.LOSS)
                );
    }

    @Test
    @DisplayName("settleFinishedGameRoom - PLAYER2_WIN이면 참가자별 LOSS/WIN record를 생성한다")
    void settleFinishedGameRoom_Player2Win_CreateLossWinRecords() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER2_WIN, SECOND_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
        given(rankCommandService.applyRecordResults(anyList())).willReturn(List.of(
                rankResult(FIRST_USER_ID, 30, 5),
                rankResult(SECOND_USER_ID, 20, 45)
        ));

        // when
        gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID);

        // then
        ArgumentCaptor<List<GameRecord>> recordsCaptor = gameRecordListCaptor();
        verify(gameRecordRepository).saveAll(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue())
                .extracting(
                        GameRecord::getUserId,
                        GameRecord::getOpponentId,
                        GameRecord::getResult,
                        GameRecord::getLpChange
                )
                .containsExactly(
                        tuple(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.LOSS, -25),
                        tuple(SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.WIN, 25)
                );
    }

    @Test
    @DisplayName("settleFinishedGameRoom - DRAW이면 두 참가자 모두 DRAW, lpChange 0 record를 생성한다")
    void settleFinishedGameRoom_Draw_CreateDrawRecords() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.DRAW, null);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
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
                .extracting(
                        GameRecord::getUserId,
                        GameRecord::getOpponentId,
                        GameRecord::getResult,
                        GameRecord::getLpBefore,
                        GameRecord::getLpAfter,
                        GameRecord::getLpChange
                )
                .containsExactly(
                        tuple(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.DRAW, 20, 20, 0),
                        tuple(SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.DRAW, 30, 30, 0)
                );
    }

    @Test
    @DisplayName("settleFinishedGameRoom - rank 정산 결과의 rankSeriesId와 seriesType을 저장한다")
    void settleFinishedGameRoom_SaveRankSeriesResult() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.DRAW, null);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
        given(rankCommandService.applyRecordResults(anyList())).willReturn(List.of(
                rankResult(FIRST_USER_ID, 10L, GameRecordSeriesType.PLACEMENT, 20, 20),
                rankResult(SECOND_USER_ID, 20L, GameRecordSeriesType.PROMOTION, 30, 30)
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
    @DisplayName("settleFinishedGameRoom - PRACTICE gameRoom이면 record/rank 정산을 하지 않는다")
    void settleFinishedGameRoom_PracticeRoom_NoOp() {
        // given
        GameRoom gameRoom = createFinishedPracticeGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when
        gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID);

        // then
        verify(gameRecordRepository, never()).countByGameRoomId(GAME_ROOM_ID);
        verify(gameRecordRepository, never()).saveAll(anyList());
        verify(rankCommandService, never()).applyRecordResults(anyList());
    }

    @Test
    @DisplayName("settleFinishedGameRoom - CUSTOM gameRoom이면 rank 변경 없이 현재 rank snapshot으로 record만 저장한다")
    void settleFinishedGameRoom_CustomRoom_SaveRecordsWithoutRankSettlement() {
        // given
        GameRoom gameRoom = createFinishedCustomGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
        given(rankReadService.getUserRankInfo(FIRST_USER_ID))
                .willReturn(rankInfo(FIRST_USER_ID, 42, Rank.of(Tier.GOLD, Division.III)));
        given(rankReadService.getUserRankInfo(SECOND_USER_ID))
                .willReturn(rankInfo(SECOND_USER_ID, 15, Rank.of(Tier.SILVER, Division.I)));

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
                        GameRecord::getRankSeriesId,
                        GameRecord::getRankBefore,
                        GameRecord::getRankAfter
                )
                .containsExactly(
                        tuple(GAME_ROOM_ID, FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.WIN,
                                42, 42, 0, GameRecordSeriesType.CUSTOM, null,
                                Rank.of(Tier.GOLD, Division.III), Rank.of(Tier.GOLD, Division.III)),
                        tuple(GAME_ROOM_ID, SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.LOSS,
                                15, 15, 0, GameRecordSeriesType.CUSTOM, null,
                                Rank.of(Tier.SILVER, Division.I), Rank.of(Tier.SILVER, Division.I))
                );
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

    @Test
    @DisplayName("settleFinishedGameRoom - rank 반영 중 예외가 발생하면 record를 저장하지 않는다")
    void settleFinishedGameRoom_RankSettlementFailed_DoNotSaveRecords() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(0L);
        willThrow(new CoreException(CoreErrorCode.RANK_NOT_FOUND))
                .given(rankCommandService)
                .applyRecordResults(anyList());

        // when & then
        assertThatThrownBy(() -> gameRecordRankSettlementService.settleFinishedGameRoom(GAME_ROOM_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.RANK_NOT_FOUND));
        verify(gameRecordRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("findUnsettledFinishedGameRoomIds - FINISHED 미정산 후보를 조회한다")
    void findUnsettledFinishedGameRoomIds() {
        // given
        int limit = 100;
        given(gameRoomRepository.findGameRoomIdsByStatusAndGameModeInAndRecordCountNot(
                GameStatus.FINISHED,
                List.of(GameMode.MATCH, GameMode.CUSTOM),
                GameRoom.MAX_PARTICIPANTS,
                PageRequest.of(0, limit)
        )).willReturn(List.of(GAME_ROOM_ID));

        // when
        List<Long> result = gameRecordRankSettlementService.findUnsettledFinishedGameRoomIds(limit);

        // then
        assertThat(result).containsExactly(GAME_ROOM_ID);
    }

    private GameRoom createFinishedGameRoom(GameResult result, Long winnerId) {
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.finish(result, winnerId);
        return gameRoom;
    }

    private GameRoom createFinishedPracticeGameRoom(GameResult result, Long winnerId) {
        GameRoom gameRoom = GameRoom.builder()
                .gameMode(GameMode.PRACTICE)
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.finish(result, winnerId);
        return gameRoom;
    }

    private GameRoom createFinishedCustomGameRoom(GameResult result, Long winnerId) {
        GameRoom gameRoom = GameRoom.builder()
                .gameMode(GameMode.CUSTOM)
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
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
        return rankResult(userId, null, GameRecordSeriesType.RANK, lpBefore, lpAfter);
    }

    private RankRecordSettlementResult rankResult(
            Long userId,
            Long rankSeriesId,
            GameRecordSeriesType seriesType,
            int lpBefore,
            int lpAfter
    ) {
        return new RankRecordSettlementResult(
                userId,
                rankSeriesId,
                seriesType,
                lpBefore,
                lpAfter,
                Rank.of(Tier.IRON, Division.IV),
                Rank.of(Tier.IRON, Division.IV)
        );
    }

    private UserRankInfo rankInfo(Long userId, int lp, Rank rank) {
        return UserRankInfo.builder()
                .userId(userId)
                .lp(lp)
                .rank(rank)
                .build();
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
