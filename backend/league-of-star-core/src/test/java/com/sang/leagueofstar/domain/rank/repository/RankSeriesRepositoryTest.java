package com.sang.leagueofstar.domain.rank.repository;

import com.sang.leagueofstar.domain.rank.domain.RankSeries;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.SeriesStatus;
import com.sang.leagueofstar.domain.rank.domain.vo.SeriesType;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RankSeriesRepositoryTest {

    private static final Long TEST_USER_ID = 1L;

    @Autowired
    private RankSeriesRepository rankSeriesRepository;

    @Test
    @DisplayName("existsByUserIdAndStatusAndType - IN_PROGRESS PLACEMENT만 active placement로 조회한다")
    void existsByUserIdAndStatusAndType_OnlyInProgressPlacement() {
        // given
        rankSeriesRepository.save(RankSeries.builder()
                .userId(TEST_USER_ID)
                .type(SeriesType.PLACEMENT)
                .status(SeriesStatus.SUCCESS)
                .totalGamesRequired(10)
                .build());
        rankSeriesRepository.save(RankSeries.builder()
                .userId(TEST_USER_ID)
                .type(SeriesType.PROMOTION)
                .targetRank(Rank.of(Tier.SILVER, Division.IV))
                .status(SeriesStatus.IN_PROGRESS)
                .totalGamesRequired(3)
                .build());
        rankSeriesRepository.save(RankSeries.builder()
                .userId(2L)
                .type(SeriesType.PLACEMENT)
                .status(SeriesStatus.IN_PROGRESS)
                .totalGamesRequired(10)
                .build());

        // when
        boolean result = rankSeriesRepository.existsByUserIdAndStatusAndType(
                TEST_USER_ID,
                SeriesStatus.IN_PROGRESS,
                SeriesType.PLACEMENT
        );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("existsByUserIdAndStatusAndType - 진행 중인 배치 시리즈가 있으면 true를 반환한다")
    void existsByUserIdAndStatusAndType_InProgressPlacement() {
        // given
        rankSeriesRepository.save(RankSeries.builder()
                .userId(TEST_USER_ID)
                .type(SeriesType.PLACEMENT)
                .status(SeriesStatus.IN_PROGRESS)
                .totalGamesRequired(10)
                .build());

        // when
        boolean result = rankSeriesRepository.existsByUserIdAndStatusAndType(
                TEST_USER_ID,
                SeriesStatus.IN_PROGRESS,
                SeriesType.PLACEMENT
        );

        // then
        assertThat(result).isTrue();
    }
}
