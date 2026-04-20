package com.sang.smite.domain.game.domain;

import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.global.domain.BaseEntity;
import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player1_id", nullable = false)
    private User player1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id", nullable = false)
    private User player2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GameStatus status = GameStatus.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GameResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(nullable = false)
    @Builder.Default
    private int dragonMaxHp = 10000;

    @Column(nullable = false)
    private int durationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_data", nullable = false)
    private GameScenario scenarioData;

    @Column(name = "game_start_time")
    private LocalDateTime gameStartTime;

    @Column
    private LocalDateTime finishedAt;

    public void start(LocalDateTime startTime) {
        this.gameStartTime = startTime;
        this.status = GameStatus.IN_PROGRESS;
    }

    public void finish(GameResult result, User winner) {
        this.result = result;
        this.winner = winner;
        this.status = GameStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
    }
}
