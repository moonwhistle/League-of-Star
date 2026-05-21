package com.sang.smite.domain.game.domain;

import com.sang.smite.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(
    name = "game_actions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_user", columnNames = {"game_room_id", "user_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class GameAction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_room_id", nullable = false)
    private Long gameRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long serverReceiveTimeMs;

    @Column(nullable = false)
    private int smiteTimeMs;

    @Column(nullable = false)
    private int dragonHpAtSmite;

    @Column(nullable = false)
    private boolean isKill;

    public static GameAction smite(Long gameRoomId,
                                   Long userId,
                                   long serverReceiveTimeMs,
                                   int smiteTimeMs,
                                   int dragonHpAtSmite) {
        return GameAction.builder()
                .gameRoomId(gameRoomId)
                .userId(userId)
                .serverReceiveTimeMs(serverReceiveTimeMs)
                .smiteTimeMs(smiteTimeMs)
                .dragonHpAtSmite(dragonHpAtSmite)
                .isKill(dragonHpAtSmite <= GameRules.SMITE_DAMAGE)
                .build();
    }
}
