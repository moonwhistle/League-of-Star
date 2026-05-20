package com.sang.smite.domain.game.domain;

import com.sang.smite.common.domain.BaseEntity;
import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.ParticipantStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
import java.util.Objects;

@Entity
@Table(name = "game_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameRoom extends BaseEntity {

    public static final int MAX_PARTICIPANTS = 2;
    public static final int DEFAULT_DRAGON_MAX_HP = 10000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_room_id")
    private List<GameParticipant> participants = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GameStatus status = GameStatus.READY;

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
                .userId(userId)
                .status(ParticipantStatus.READY)
                .build();
        this.participants.add(participant);
    }

    public boolean hasParticipant(Long userId) {
        return this.participants.stream()
                .anyMatch(participant -> Objects.equals(participant.getUserId(), userId));
    }

    public void start(LocalDateTime startTime) {
        validateStart();
        this.gameStartTime = startTime;
        this.status = GameStatus.IN_PROGRESS;
        this.participants.forEach(p -> p.updateStatus(ParticipantStatus.PLAYING));
    }

    private void validateStart() {
        if (this.status != GameStatus.READY) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
        if (this.participants.size() != MAX_PARTICIPANTS) {
            throw new CoreException(CoreErrorCode.INCOMPLETE_PARTICIPANTS);
        }
        boolean allReady = this.participants.stream()
                .allMatch(p -> p.getStatus() == ParticipantStatus.READY);
        if (!allReady) {
            throw new CoreException(CoreErrorCode.INCOMPLETE_PARTICIPANTS);
        }
    }

    public void finish(GameResult result, Long winnerId) {
        this.result = result;
        this.winnerId = winnerId;
        this.status = GameStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
        this.participants.forEach(p -> p.updateStatus(ParticipantStatus.FINISHED));
    }

    public void abortBeforeStart() {
        if (abortBeforeStartIfReady()) {
            return;
        }
        if (this.status != GameStatus.ABORTED) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
    }

    /**
     * READY 상태인 GAME_START 이전 gameRoom만 ABORTED로 전환합니다.
     *
     * @return READY에서 ABORTED로 전환했으면 true, 이미 다른 상태이면 false
     */
    public boolean abortBeforeStartIfReady() {
        if (this.status != GameStatus.READY) {
            return false;
        }
        this.status = GameStatus.ABORTED;
        this.finishedAt = LocalDateTime.now();
        this.participants.forEach(p -> p.updateStatus(ParticipantStatus.ABORTED));
        return true;
    }

    /**
     * GAME_START 이후 서버가 게임 종료를 보장할 수 없는 경우 gameRoom을 ABORTED로 전환합니다.
     *
     * @return IN_PROGRESS에서 ABORTED로 전환했으면 true, 이미 다른 상태이면 false
     */
    public boolean abortAfterStartIfInProgress() {
        if (this.status != GameStatus.IN_PROGRESS) {
            return false;
        }
        this.status = GameStatus.ABORTED;
        this.finishedAt = LocalDateTime.now();
        this.participants.forEach(p -> p.updateStatus(ParticipantStatus.ABORTED));
        return true;
    }
}
