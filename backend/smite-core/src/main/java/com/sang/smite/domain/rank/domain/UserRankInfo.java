package com.sang.smite.domain.rank.domain;

import com.sang.smite.common.domain.BaseEntity;
import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_rank_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserRankInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Embedded
    @Builder.Default
    private Rank rank = Rank.of(Tier.IRON, Division.IV);

    @Column(nullable = false)
    @Builder.Default
    private int lp = 0;

    @Column(nullable = false)
    @Builder.Default
    private int tierScore = 1;

    @Column(nullable = false)
    @Builder.Default
    private int totalWins = 0;

    @Column(nullable = false)
    @Builder.Default
    private int totalLosses = 0;

    @Column(nullable = false)
    @Builder.Default
    private int totalDraws = 0;

    public void updateLp(int amount) {
        this.lp += amount;
        if (this.lp < 0) this.lp = 0;
    }

    public void updateRankAndLp(Rank rank, int lp) {
        this.rank = rank;
        this.lp = Math.max(lp, 0);
    }

    public void applyRecordResult(GameRecordResult result) {
        switch (result) {
            case WIN -> this.totalWins++;
            case LOSS -> this.totalLosses++;
            case DRAW -> this.totalDraws++;
        }
    }

    public int getTierScore() {
        return rank.getTierScore();
    }
}
