package com.sang.smite.domain.rank.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record Rank(
        @Enumerated(EnumType.STRING) @Column(name = "tier", nullable = false, length = 20) Tier tier,

        @Enumerated(EnumType.STRING) @Column(name = "division", length = 5) Division division)
        implements Comparable<Rank> {

    public static Rank of(Tier tier, Division division) {
        return new Rank(tier, division);
    }

    /**
     * [1단계: 티어 점수화] 랭크의 매칭 및 LP 계산 기준이 되는 티어 점수(Tier Score)를 계산합니다.
     * <p>
     * 이 점수는 상대방과의 실력 차이(gap)를 구하기 위한 지표로 사용됩니다.
     * </p>
     * 공식: {@code (Tier_Level - 1) * 4 + (4 - Division_Value) + 1}
     *
     * @return 1 ~ 28 사이의 정수 점수 (Apex 티어 제외)
     * @see <a href="./smite/docs/project/policy.md">정책 문서 3.1 & 3.2 참조</a>
     */
    public int getTierScore() {
        int baseScore = (this.tier.getLevel() - 1) * 4;
        int divisionBonus = (division != null) ? (4 - division.getValue()) : 0;
        return baseScore + divisionBonus + 1;
    }

    @Override
    public int compareTo(Rank other) {
        return Integer.compare(this.getTierScore(), other.getTierScore());
    }

    /**
     * [2단계: LP 변동량 계산] 상대방 랭크와의 점수 차이(gap)를 기반으로 승리 시 획득할 LP를 계산합니다.
     * <p>
     * 공식: {@code clamp(25 + gap * 3, 15, 35)}
     * </p>
     *
     * @param opponentRank 상대방의 Rank VO
     * @return 15 ~ 35 사이의 승리 획득 LP
     */
    public int calculateWinLp(Rank opponentRank) {
        int gap = opponentRank.getTierScore() - this.getTierScore();
        return Math.max(15, Math.min(35, 25 + (gap * 3)));
    }

    /**
     * [2단계: LP 변동량 계산] 상대방 랭크와의 점수 차이(gap)를 기반으로 패배 시 차감될 LP를 계산합니다.
     * <p>
     * 공식: {@code clamp(25 - gap * 3, 15, 35)}
     * </p>
     *
     * @param opponentRank 상대방의 Rank VO
     * @return 15 ~ 35 사이의 패배 차감 LP
     */
    public int calculateLossLp(Rank opponentRank) {
        int gap = opponentRank.getTierScore() - this.getTierScore();
        return Math.max(15, Math.min(35, 25 - (gap * 3)));
    }

    @Override
    public String toString() {
        return tier.getDescription() + (division != null ? " " + division.name() : "");
    }
}
