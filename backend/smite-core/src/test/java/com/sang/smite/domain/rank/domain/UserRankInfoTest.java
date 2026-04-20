package com.sang.smite.domain.rank.domain;

import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRankInfoTest {

    @Test
    @DisplayName("LP 업데이트 시 0점 미만으로 내려가지 않아야 한다")
    void updateLp_ShouldNotGoBelowZero() {
        // given
        UserRankInfo rankInfo = UserRankInfo.builder()
                .lp(10)
                .build();

        // when
        rankInfo.updateLp(-25);

        // then
        assertThat(rankInfo.getLp()).isZero();
    }

    @Test
    @DisplayName("LP 업데이트 시 양수 값은 정상적으로 합산되어야 한다")
    void updateLp_ShouldIncreaseCorrectly() {
        // given
        UserRankInfo rankInfo = UserRankInfo.builder()
                .lp(10)
                .build();

        // when
        rankInfo.updateLp(25);

        // then
        assertThat(rankInfo.getLp()).isEqualTo(35);
    }

    @Test
    @DisplayName("getTierScore 호출 시 랭크 VO의 점수 계산 로직을 정확히 반환해야 한다")
    void getTierScore_ShouldDelegateToRankVO() {
        // given
        Rank rank = Rank.of(Tier.SILVER, Division.I); // 12점
        UserRankInfo rankInfo = UserRankInfo.builder()
                .rank(rank)
                .build();

        // when
        int score = rankInfo.getTierScore();

        // then
        assertThat(score).isEqualTo(12);
    }
}
