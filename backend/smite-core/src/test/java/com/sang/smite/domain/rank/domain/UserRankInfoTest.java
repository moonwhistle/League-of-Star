package com.sang.smite.domain.rank.domain;

import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
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

    @Test
    @DisplayName("applyRecordResult - WIN/LOSS/DRAW 누적 전적을 캡슐화해서 반영한다")
    void applyRecordResult() {
        // given
        UserRankInfo rankInfo = UserRankInfo.builder()
                .build();

        // when
        rankInfo.applyRecordResult(GameRecordResult.WIN);
        rankInfo.applyRecordResult(GameRecordResult.LOSS);
        rankInfo.applyRecordResult(GameRecordResult.DRAW);

        // then
        assertThat(rankInfo.getTotalWins()).isEqualTo(1);
        assertThat(rankInfo.getTotalLosses()).isEqualTo(1);
        assertThat(rankInfo.getTotalDraws()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateRankAndLp - 랭크와 LP를 함께 갱신한다")
    void updateRankAndLp() {
        // given
        UserRankInfo rankInfo = UserRankInfo.builder()
                .rank(Rank.of(Tier.IRON, Division.IV))
                .lp(10)
                .build();

        // when
        rankInfo.updateRankAndLp(Rank.of(Tier.BRONZE, Division.I), 75);

        // then
        assertThat(rankInfo.getRank()).isEqualTo(Rank.of(Tier.BRONZE, Division.I));
        assertThat(rankInfo.getLp()).isEqualTo(75);
    }
}
