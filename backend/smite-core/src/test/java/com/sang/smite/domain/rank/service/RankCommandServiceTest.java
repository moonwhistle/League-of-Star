package com.sang.smite.domain.rank.service;

import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.domain.vo.SeriesType;
import com.sang.smite.domain.rank.repository.RankSeriesRepository;
import com.sang.smite.domain.rank.repository.UserRankInfoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankCommandServiceTest {

    private static final Long TEST_USER_ID = 1L;

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
}
