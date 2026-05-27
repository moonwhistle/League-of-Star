package com.sang.smite.domain.rank.domain;

import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.SeriesStatus;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.rank.domain.vo.Division;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankSeriesTest {

    private static final Long TEST_USER_ID = 1L;

    @Test
    @DisplayName("Placement - 10판을 채우면 SUCCESS 상태가 된다")
    void placement_Completion() {
        // given
        RankSeries series = RankSeries.createPlacement(TEST_USER_ID);

        // when (9판까지는 진행 중)
        for (int i = 0; i < 9; i++) {
            series.addWin();
        }
        
        // then
        assertThat(series.getStatus()).isEqualTo(SeriesStatus.IN_PROGRESS);

        // when (10판째)
        series.addLoss();

        // then
        assertThat(series.getStatus()).isEqualTo(SeriesStatus.SUCCESS);
        assertThat(series.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Promotion - 2승을 먼저 달성하면 SUCCESS 상태가 된다")
    void promotion_Success() {
        // given
        Rank targetRank = Rank.of(Tier.GOLD, Division.IV);
        RankSeries series = RankSeries.createPromotion(TEST_USER_ID, targetRank);

        // when
        series.addWin();
        series.addLoss();
        series.addWin(); // 2승 1패

        // then
        assertThat(series.getStatus()).isEqualTo(SeriesStatus.SUCCESS);
        assertThat(series.getWins()).isEqualTo(2);
    }

    @Test
    @DisplayName("Promotion - 2패를 먼저 달성하면 FAILED 상태가 된다")
    void promotion_Failure() {
        // given
        Rank targetRank = Rank.of(Tier.GOLD, Division.IV);
        RankSeries series = RankSeries.createPromotion(TEST_USER_ID, targetRank);

        // when
        series.addLoss();
        series.addLoss(); // 0승 2패

        // then
        assertThat(series.getStatus()).isEqualTo(SeriesStatus.FAILED);
        assertThat(series.getLosses()).isEqualTo(2);
    }

    @Test
    @DisplayName("Promotion - 남은 판으로 2승이 불가능하면 FAILED 상태가 된다")
    void promotion_FailureWhenTwoWinsImpossible() {
        // given
        Rank targetRank = Rank.of(Tier.GOLD, Division.IV);
        RankSeries series = RankSeries.createPromotion(TEST_USER_ID, targetRank);

        // when
        series.addDraw();
        series.addDraw();

        // then
        assertThat(series.getStatus()).isEqualTo(SeriesStatus.FAILED);
        assertThat(series.getDraws()).isEqualTo(2);
    }
}
