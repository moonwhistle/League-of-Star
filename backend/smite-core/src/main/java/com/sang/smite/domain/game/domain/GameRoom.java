package com.sang.smite.domain.game.domain;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.ParticipantStatus;
import com.sang.smite.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameRoom extends BaseEntity {

    public static final int MAX_PARTICIPANTS = 2;
    public static final int DEFAULT_DRAGON_MAX_HP = 10000;
    private static final String ERR_MAX_PARTICIPANTS = "1v1 게임방에는 최대 %d명까지만 참여 가능합니다.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @OneToMany(mappedBy = "gameRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameParticipant> participants = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GameStatus status = GameStatus.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GameResult result;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(nullable = false)
    @Builder.Default
    private int dragonMaxHp = DEFAULT_DRAGON_MAX_HP;

    @Column(nullable = false)
    private int durationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_data", nullable = false)
    private GameScenario scenarioData;

    @Column(name = "game_start_time")
    private LocalDateTime gameStartTime;

    @Column
    private LocalDateTime finishedAt;

    public void addParticipant(Long userId) {
        if (this.participants.size() >= MAX_PARTICIPANTS) {
            throw new CoreException(CoreErrorCode.GAME_ROOM_FULL);
        }
        GameParticipant participant = GameParticipant.builder()
                .gameRoom(this)
                .userId(userId)
                .status(ParticipantStatus.READY)
                .build();
        this.participants.add(participant);
    }

    public void start(LocalDateTime startTime) {
        this.gameStartTime = startTime;
        this.status = GameStatus.IN_PROGRESS;
        this.participants.forEach(p -> p.updateStatus(ParticipantStatus.PLAYING));
    }

    public void finish(GameResult result, Long winnerId) {
        this.result = result;
        this.winnerId = winnerId;
        this.status = GameStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
        this.participants.forEach(p -> p.updateStatus(ParticipantStatus.FINISHED));
    }
}
