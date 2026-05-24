package com.sang.smite.domain.rank.service;

import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.SeriesType;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementCommand;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementResult;
import com.sang.smite.domain.rank.repository.RankSeriesRepository;
import com.sang.smite.domain.rank.repository.UserRankInfoRepository;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankCommandServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final Long OPPONENT_USER_ID = 2L;

    @InjectMocks
    private RankCommandService rankCommandService;

    @Mock
    private UserRankInfoRepository userRankInfoRepository;

    @Mock
    private RankSeriesRepository rankSeriesRepository;

    @Test
    @DisplayName("initializeRank - 초기 랭크 정보를 생성하고 배치 고사를 시작한다")
    void initializeRank_Success() {
        // when
        rankCommandService.initializeRank(TEST_USER_ID);

        // then
        // 1. UserRankInfo 저장 검증 (초기 티어 점수 1 확인)
        ArgumentCaptor<UserRankInfo> rankInfoCaptor = ArgumentCaptor.forClass(UserRankInfo.class);
        verify(userRankInfoRepository, times(1)).save(rankInfoCaptor.capture());
        
        UserRankInfo capturedRankInfo = rankInfoCaptor.getValue();
        assertThat(capturedRankInfo.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(capturedRankInfo.getTierScore()).isEqualTo(1);

        // 2. RankSeries(PLACEMENT) 시작 검증
        ArgumentCaptor<RankSeries> seriesCaptor = ArgumentCaptor.forClass(RankSeries.class);
        verify(rankSeriesRepository, times(1)).save(seriesCaptor.capture());
        
        RankSeries capturedSeries = seriesCaptor.getValue();
        assertThat(capturedSeries.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(capturedSeries.getType()).isEqualTo(SeriesType.PLACEMENT);
        assertThat(capturedSeries.getTotalGamesRequired()).isEqualTo(10);
    }

    @Test
    @DisplayName("applyRecordResults - RANK WIN/LOSS는 누적 전적과 LP를 반영하고 before snapshot 기준으로 계산한다")
    void applyRecordResults_RankWinLoss() {
        // given
        UserRankInfo user = rankInfo(TEST_USER_ID, Rank.of(Tier.SILVER, Division.IV), 20);
        UserRankInfo opponent = rankInfo(OPPONENT_USER_ID, Rank.of(Tier.SILVER, Division.IV), 30);
        given(userRankInfoRepository.findByUserIdForUpdate(TEST_USER_ID)).willReturn(Optional.of(user));
        given(userRankInfoRepository.findByUserIdForUpdate(OPPONENT_USER_ID)).willReturn(Optional.of(opponent));

        // when
        List<RankRecordSettlementResult> results = rankCommandService.applyRecordResults(List.of(
                new RankRecordSettlementCommand(
                        TEST_USER_ID,
                        OPPONENT_USER_ID,
                        GameRecordResult.WIN,
                        GameRecordSeriesType.RANK
                ),
                new RankRecordSettlementCommand(
                        OPPONENT_USER_ID,
                        TEST_USER_ID,
                        GameRecordResult.LOSS,
                        GameRecordSeriesType.RANK
                )
        ));

        // then
        assertThat(user.getTotalWins()).isEqualTo(1);
        assertThat(user.getLp()).isEqualTo(45);
        assertThat(opponent.getTotalLosses()).isEqualTo(1);
        assertThat(opponent.getLp()).isEqualTo(5);
        assertThat(results)
                .extracting(
                        RankRecordSettlementResult::userId,
                        RankRecordSettlementResult::lpBefore,
                        RankRecordSettlementResult::lpAfter,
                        RankRecordSettlementResult::rankBefore,
                        RankRecordSettlementResult::rankAfter
                )
                .containsExactly(
                        tuple(TEST_USER_ID, 20, 45, Rank.of(Tier.SILVER, Division.IV),
                                Rank.of(Tier.SILVER, Division.IV)),
                        tuple(OPPONENT_USER_ID, 30, 5, Rank.of(Tier.SILVER, Division.IV),
                                Rank.of(Tier.SILVER, Division.IV))
                );
        verify(rankSeriesRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyRecordResults - DRAW는 누적 무승부만 반영하고 LP를 변경하지 않는다")
    void applyRecordResults_Draw() {
        // given
        UserRankInfo user = rankInfo(TEST_USER_ID, Rank.of(Tier.SILVER, Division.IV), 20);
        UserRankInfo opponent = rankInfo(OPPONENT_USER_ID, Rank.of(Tier.SILVER, Division.IV), 30);
        given(userRankInfoRepository.findByUserIdForUpdate(TEST_USER_ID)).willReturn(Optional.of(user));
        given(userRankInfoRepository.findByUserIdForUpdate(OPPONENT_USER_ID)).willReturn(Optional.of(opponent));

        // when
        List<RankRecordSettlementResult> results = rankCommandService.applyRecordResults(List.of(
                new RankRecordSettlementCommand(
                        TEST_USER_ID,
                        OPPONENT_USER_ID,
                        GameRecordResult.DRAW,
                        GameRecordSeriesType.RANK
                )
        ));

        // then
        assertThat(user.getTotalDraws()).isEqualTo(1);
        assertThat(user.getLp()).isEqualTo(20);
        assertThat(results.get(0).lpBefore()).isEqualTo(20);
        assertThat(results.get(0).lpAfter()).isEqualTo(20);
    }

    @Test
    @DisplayName("applyRecordResults - PLACEMENT/PROMOTION은 누적 전적만 반영하고 LP를 동결한다")
    void applyRecordResults_SeriesFreezeLp() {
        // given
        UserRankInfo user = rankInfo(TEST_USER_ID, Rank.of(Tier.SILVER, Division.IV), 20);
        UserRankInfo opponent = rankInfo(OPPONENT_USER_ID, Rank.of(Tier.SILVER, Division.IV), 30);
        given(userRankInfoRepository.findByUserIdForUpdate(TEST_USER_ID)).willReturn(Optional.of(user));
        given(userRankInfoRepository.findByUserIdForUpdate(OPPONENT_USER_ID)).willReturn(Optional.of(opponent));

        // when
        List<RankRecordSettlementResult> results = rankCommandService.applyRecordResults(List.of(
                new RankRecordSettlementCommand(
                        TEST_USER_ID,
                        OPPONENT_USER_ID,
                        GameRecordResult.WIN,
                        GameRecordSeriesType.PLACEMENT
                )
        ));

        // then
        assertThat(user.getTotalWins()).isEqualTo(1);
        assertThat(user.getLp()).isEqualTo(20);
        assertThat(results.get(0).lpAfter()).isEqualTo(20);
        verify(rankSeriesRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyRecordResults - 일반 RANK 승리로 99LP 이상이 되면 승급전을 시작하고 LP 100으로 동결한다")
    void applyRecordResults_StartPromotionSeries() {
        // given
        UserRankInfo user = rankInfo(TEST_USER_ID, Rank.of(Tier.SILVER, Division.I), 90);
        UserRankInfo opponent = rankInfo(OPPONENT_USER_ID, Rank.of(Tier.SILVER, Division.I), 30);
        given(userRankInfoRepository.findByUserIdForUpdate(TEST_USER_ID)).willReturn(Optional.of(user));
        given(userRankInfoRepository.findByUserIdForUpdate(OPPONENT_USER_ID)).willReturn(Optional.of(opponent));

        // when
        List<RankRecordSettlementResult> results = rankCommandService.applyRecordResults(List.of(
                new RankRecordSettlementCommand(
                        TEST_USER_ID,
                        OPPONENT_USER_ID,
                        GameRecordResult.WIN,
                        GameRecordSeriesType.RANK
                )
        ));

        // then
        assertThat(user.getLp()).isEqualTo(100);
        assertThat(user.getRank()).isEqualTo(Rank.of(Tier.SILVER, Division.I));
        assertThat(results.get(0).lpBefore()).isEqualTo(90);
        assertThat(results.get(0).lpAfter()).isEqualTo(100);

        ArgumentCaptor<RankSeries> seriesCaptor = ArgumentCaptor.forClass(RankSeries.class);
        verify(rankSeriesRepository).save(seriesCaptor.capture());
        assertThat(seriesCaptor.getValue().getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(seriesCaptor.getValue().getType()).isEqualTo(SeriesType.PROMOTION);
        assertThat(seriesCaptor.getValue().getTargetRank()).isEqualTo(Rank.of(Tier.GOLD, Division.IV));
    }

    @Test
    @DisplayName("applyRecordResults - LP 0에서 일반 RANK 패배 시 이전 rank LP 75로 강등한다")
    void applyRecordResults_DemoteOnZeroLpLoss() {
        // given
        UserRankInfo user = rankInfo(TEST_USER_ID, Rank.of(Tier.SILVER, Division.IV), 0);
        UserRankInfo opponent = rankInfo(OPPONENT_USER_ID, Rank.of(Tier.SILVER, Division.IV), 30);
        given(userRankInfoRepository.findByUserIdForUpdate(TEST_USER_ID)).willReturn(Optional.of(user));
        given(userRankInfoRepository.findByUserIdForUpdate(OPPONENT_USER_ID)).willReturn(Optional.of(opponent));

        // when
        List<RankRecordSettlementResult> results = rankCommandService.applyRecordResults(List.of(
                new RankRecordSettlementCommand(
                        TEST_USER_ID,
                        OPPONENT_USER_ID,
                        GameRecordResult.LOSS,
                        GameRecordSeriesType.RANK
                )
        ));

        // then
        assertThat(user.getRank()).isEqualTo(Rank.of(Tier.BRONZE, Division.I));
        assertThat(user.getLp()).isEqualTo(75);
        assertThat(results.get(0).rankBefore()).isEqualTo(Rank.of(Tier.SILVER, Division.IV));
        assertThat(results.get(0).rankAfter()).isEqualTo(Rank.of(Tier.BRONZE, Division.I));
    }

    private UserRankInfo rankInfo(Long userId, Rank rank, int lp) {
        return UserRankInfo.builder()
                .userId(userId)
                .rank(rank)
                .lp(lp)
                .build();
    }
}
