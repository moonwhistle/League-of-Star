package com.sang.smite.domain.record.domain;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.global.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "game_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_room_user", columnNames = {"game_room_id", "user_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_room_id", nullable = false)
    private GameRoom gameRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_id", nullable = false)
    private User opponent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GameRecordResult result;

    @Column(nullable = false)
    private int lpChange;

    @Column(nullable = false)
    private int lpBefore;

    @Column(nullable = false)
    private int lpAfter;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tier", column = @Column(name = "tier_before", nullable = false)),
        @AttributeOverride(name = "division", column = @Column(name = "division_before"))
    })
    private Rank rankBefore;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "tier", column = @Column(name = "tier_after", nullable = false)),
        @AttributeOverride(name = "division", column = @Column(name = "division_after"))
    })
    private Rank rankAfter;

    @Column(nullable = false)
    @Builder.Default
    private boolean isPromotionGame = false;
}
