package com.sang.leagueofstar.domain.rank.domain;

import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.SeriesStatus;
import com.sang.leagueofstar.domain.rank.domain.vo.SeriesType;
import com.sang.leagueofstar.common.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rank_series")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RankSeries extends BaseEntity {

    private static final int PLACEMENT_GAMES = 10;
    private static final int PROMOTION_GAMES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeriesType type;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tier", column = @Column(name = "target_tier")),
        @AttributeOverride(name = "division", column = @Column(name = "target_division"))
    })
    private Rank targetRank;

    @Column(nullable = false)
    @Builder.Default
    private int wins = 0;

    @Column(nullable = false)
    @Builder.Default
    private int losses = 0;

    @Column(nullable = false)
    @Builder.Default
    private int draws = 0;

    @Column(nullable = false)
    private int totalGamesRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeriesStatus status = SeriesStatus.IN_PROGRESS;

    @Column
    private LocalDateTime completedAt;

    public static RankSeries createPlacement(Long userId) {
        return RankSeries.builder()
                .userId(userId)
                .type(SeriesType.PLACEMENT)
                .totalGamesRequired(PLACEMENT_GAMES)
                .build();
    }

    public static RankSeries createPromotion(Long userId, Rank targetRank) {
        return RankSeries.builder()
                .userId(userId)
                .type(SeriesType.PROMOTION)
                .targetRank(targetRank)
                .totalGamesRequired(PROMOTION_GAMES)
                .build();
    }

    public void addWin() {
        this.wins++;
        checkCompletion();
    }

    public void addLoss() {
        this.losses++;
        checkCompletion();
    }

    public void addDraw() {
        this.draws++;
        checkCompletion();
    }

    /**
     * 시리즈의 완료 여부를 판단합니다.
     * <p>
     * [정책 4.4] 배치 고사: 총 10판의 게임을 수행하면 완료됩니다.
     * [정책 4.2] 승급전: 3판 2선승제(Best of 3)를 따릅니다.
     * - 2승 도달 시: 성공(SUCCESS)
     * - 2패 도달 시: 실패(FAILED)
     * </p>
     */
    private void checkCompletion() {
        int currentGames = wins + losses + draws;
        
        if (type == SeriesType.PLACEMENT) {
            if (currentGames >= totalGamesRequired) {
                complete(SeriesStatus.SUCCESS);
            }
        } else if (type == SeriesType.PROMOTION) {
            // 3판 2선승제 계산: (3 / 2) + 1 = 2
            int threshold = (totalGamesRequired / 2) + 1;
            int remainingGames = totalGamesRequired - currentGames;
            
            if (wins >= threshold) {
                complete(SeriesStatus.SUCCESS);
            } else if (losses >= threshold || wins + remainingGames < threshold) {
                complete(SeriesStatus.FAILED);
            }
        }
    }

    private void complete(SeriesStatus finalStatus) {
        this.status = finalStatus;
        this.completedAt = LocalDateTime.now();
    }
}
