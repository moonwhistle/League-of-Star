package com.sang.leagueofstar.domain.rank.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RankTest {

    @ParameterizedTest
    @CsvSource({
        "IRON, IV, 1",
        "IRON, I, 4",
        "BRONZE, IV, 5",
        "SILVER, I, 12",
        "GOLD, IV, 13",
        "DIAMOND, I, 28"
    })
    @DisplayName("티어와 디비전에 따른 티어 점수(1~28) 계산이 정확해야 한다")
    void getTierScore_ShouldCalculateCorrectly(Tier tier, Division division, int expectedScore) {
        // given
        Rank rank = Rank.of(tier, division);

        // when
        int score = rank.getTierScore();

        // then
        assertThat(score).isEqualTo(expectedScore);
    }

    @Test
    @DisplayName("나와 상대방의 랭크가 동일하면 승리 시 25 LP를 획득한다")
    void calculateWinLp_ShouldReturn25_WhenRanksAreEqual() {
        // given
        Rank myRank = Rank.of(Tier.SILVER, Division.IV); // 9점
        Rank opponentRank = Rank.of(Tier.SILVER, Division.IV); // 9점

        // when
        int winLp = myRank.calculateWinLp(opponentRank);

        // then
        assertThat(winLp).isEqualTo(25);
    }

    @Test
    @DisplayName("상대방의 티어 점수가 2점 높으면 승리 시 31 LP를 획득한다")
    void calculateWinLp_ShouldReturn31_WhenOpponentIsHigherBy2() {
        // given
        Rank myRank = Rank.of(Tier.SILVER, Division.IV); // 9점
        Rank opponentRank = Rank.of(Tier.SILVER, Division.II); // 11점 (gap = 2)

        // when
        int winLp = myRank.calculateWinLp(opponentRank);

        // then
        assertThat(winLp).isEqualTo(31); // 25 + (2 * 3)
    }

    @Test
    @DisplayName("상대방의 티어 점수가 매우 높으면 최대 35 LP까지만 획득한다")
    void calculateWinLp_ShouldBeClampedAt35() {
        // given
        Rank myRank = Rank.of(Tier.IRON, Division.IV); // 1점
        Rank opponentRank = Rank.of(Tier.DIAMOND, Division.I); // 28점 (gap = 27)

        // when
        int winLp = myRank.calculateWinLp(opponentRank);

        // then
        assertThat(winLp).isEqualTo(35);
    }

    @Test
    @DisplayName("상대방의 티어 점수가 매우 낮으면 최소 15 LP까지만 하락한다")
    void calculateLossLp_ShouldBeClampedAt15() {
        // given
        Rank myRank = Rank.of(Tier.DIAMOND, Division.I); // 28점
        Rank opponentRank = Rank.of(Tier.IRON, Division.IV); // 1점 (gap = -27)

        // when
        int lossLp = myRank.calculateLossLp(opponentRank);

        // then
        assertThat(lossLp).isEqualTo(35); // 25 - (-27 * 3) = 106 -> clamp 35
    }

    @Test
    @DisplayName("랭크 비교 시 티어 점수가 높으면 더 큰 랭크로 판정한다")
    void compareTo_ShouldWorkCorrectly() {
        // given
        Rank lowRank = Rank.of(Tier.IRON, Division.I);
        Rank highRank = Rank.of(Tier.BRONZE, Division.IV);

        // when & then
        assertThat(lowRank).isLessThan(highRank);
        assertThat(highRank).isGreaterThan(lowRank);
    }

    @Test
    @DisplayName("name - 일반 랭크는 TIER_DIVISION 형식으로 반환한다")
    void name_ReturnTierDivision() {
        // given
        Rank rank = Rank.of(Tier.GOLD, Division.IV);

        // when
        String result = rank.name();

        // then
        assertThat(result).isEqualTo("GOLD_IV");
    }

    @Test
    @DisplayName("name - division이 없는 랭크는 TIER 형식으로 반환한다")
    void name_ReturnTierOnly() {
        // given
        Rank rank = Rank.of(Tier.MASTER, null);

        // when
        String result = rank.name();

        // then
        assertThat(result).isEqualTo("MASTER");
    }
}
