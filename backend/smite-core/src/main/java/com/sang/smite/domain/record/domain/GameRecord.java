package com.sang.smite.domain.record.domain;

import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "game_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_records_room_user", columnNames = {"game_room_id", "user_id"})
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

    @Column(name = "game_room_id", nullable = false)
    private Long gameRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "opponent_id", nullable = false)
    private Long opponentId;

    @Column(name = "promotion_series_id")
    private Long promotionSeriesId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GameRecordResult result;

    @Column(nullable = false)
    private int lpChange;

    @Column(nullable = false)
    private int lpBefore;

    @Column(nullable = false)
    private int lpAfter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rank_before", nullable = false)
    private Rank rankBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rank_after", nullable = false)
    private Rank rankAfter;

    @Column(nullable = false)
    @Builder.Default
    private boolean isPromotionGame = false;
}
