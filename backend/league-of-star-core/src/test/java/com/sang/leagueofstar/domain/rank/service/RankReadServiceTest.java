package com.sang.leagueofstar.domain.rank.service;

import com.sang.leagueofstar.domain.rank.domain.vo.SeriesStatus;
import com.sang.leagueofstar.domain.rank.domain.vo.SeriesType;
import com.sang.leagueofstar.domain.rank.repository.RankSeriesRepository;
import com.sang.leagueofstar.domain.rank.repository.UserRankInfoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RankReadServiceTest {

    private static final Long TEST_USER_ID = 1L;

    @InjectMocks
    private RankReadService rankReadService;

    @Mock
    private UserRankInfoRepository userRankInfoRepository;

    @Mock
    private RankSeriesRepository rankSeriesRepository;

    @Test
    @DisplayName("isPlacementInProgress - IN_PROGRESS PLACEMENT 존재 여부를 반환한다")
    void isPlacementInProgress_True() {
        // given
        given(rankSeriesRepository.existsByUserIdAndStatusAndType(
                TEST_USER_ID,
                SeriesStatus.IN_PROGRESS,
                SeriesType.PLACEMENT
        )).willReturn(true);

        // when
        boolean result = rankReadService.isPlacementInProgress(TEST_USER_ID);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isPlacementInProgress - 진행 중인 배치 시리즈가 없으면 false를 반환한다")
    void isPlacementInProgress_False() {
        // given
        given(rankSeriesRepository.existsByUserIdAndStatusAndType(
                TEST_USER_ID,
                SeriesStatus.IN_PROGRESS,
                SeriesType.PLACEMENT
        )).willReturn(false);

        // when
        boolean result = rankReadService.isPlacementInProgress(TEST_USER_ID);

        // then
        assertThat(result).isFalse();
    }
}
