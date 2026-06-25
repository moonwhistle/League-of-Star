package com.sang.leagueofstar.domain.rank.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 티어와 단계(Division)를 결합한 가치 객체(Value Object)입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Rank(
        @Enumerated(EnumType.STRING)
        @Column(name = "tier", nullable = false, length = 20) Tier tier,
        @Enumerated(EnumType.STRING)
        @Column(name = "division", length = 5) Division division
)
        implements Comparable<Rank> {

    private static final int DIVISION_COUNT = 4;
    private static final int BASE_LP_CHANGE = 25;
    private static final int GAP_MULTIPLIER = 3;
    private static final int MIN_LP_CHANGE = 15;
    private static final int MAX_LP_CHANGE = 35;

    public static Rank of(Tier tier, Division division) {
        return new Rank(tier, division);
    }

    /**
     * [정책 3.2] 티어 점수(Tier Score)를 계산합니다.
     * <p>
     * 공식: (Tier_Level - 1) * 4 + (4 - Division_Value) + 1
     * </p>
     * @return Iron IV(1) ~ Diamond I(28) 범위의 고유 점수
     */
    public int getTierScore() {
        int baseScore = (this.tier.getLevel() - 1) * DIVISION_COUNT;
        int divisionBonus = (division != null) ? (DIVISION_COUNT - division.getValue()) : 0;
        return baseScore + divisionBonus + 1;
    }

    @Override
    public int compareTo(Rank other) {
        return Integer.compare(this.getTierScore(), other.getTierScore());
    }

    public String name() {
        if (division == null) {
            return tier.name();
        }
        return tier.name() + "_" + division.name();
    }

    /**
     * [정책 3.1] 승리 시 획득할 LP를 계산합니다.
     * <p>
     * 공식: clamp(25 + gap * 3, 15, 35)
     * gap: (상태 티어 점수 - 내 티어 점수)
     * </p>
     * @param opponentRank 상대방의 랭크 정보
     * @return 최소 15, 최대 35 범위의 획득 LP
     */
    public int calculateWinLp(Rank opponentRank) {
        int gap = opponentRank.getTierScore() - this.getTierScore();
        return Math.max(MIN_LP_CHANGE, Math.min(MAX_LP_CHANGE, BASE_LP_CHANGE + (gap * GAP_MULTIPLIER)));
    }

    /**
     * [정책 3.1] 패배 시 차감될 LP를 계산합니다.
     * <p>
     * 공식: clamp(25 - gap * 3, 15, 35)
     * gap: (상태 티어 점수 - 내 티어 점수)
     * </p>
     * @param opponentRank 상대방의 랭크 정보
     * @return 최소 15, 최대 35 범위의 차감 LP
     */
    public int calculateLossLp(Rank opponentRank) {
        int gap = opponentRank.getTierScore() - this.getTierScore();
        return Math.max(MIN_LP_CHANGE, Math.min(MAX_LP_CHANGE, BASE_LP_CHANGE - (gap * GAP_MULTIPLIER)));
    }

    @Override
    public String toString() {
        return tier.getDescription() + (division != null ? " " + division.name() : "");
    }
}
