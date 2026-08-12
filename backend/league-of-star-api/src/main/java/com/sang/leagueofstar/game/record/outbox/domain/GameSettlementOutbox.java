package com.sang.leagueofstar.game.record.outbox.domain;

import com.sang.leagueofstar.common.domain.BaseEntity;
import com.sang.leagueofstar.domain.game.event.GameFinishedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "game_settlement_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_settlement_outbox_game_room",
                columnNames = "game_room_id"
        ),
        indexes = {
                @Index(name = "idx_game_settlement_outbox_retry", columnList = "status,next_attempt_at"),
                @Index(name = "idx_game_settlement_outbox_lease", columnList = "status,locked_until")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSettlementOutbox extends BaseEntity {

    @Id
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "game_room_id", nullable = false, updatable = false)
    private Long gameRoomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameSettlementOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "lock_token", length = 36)
    private String lockToken;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    private GameSettlementOutbox(GameFinishedEvent event, LocalDateTime now) {
        this.eventId = event.eventId();
        this.gameRoomId = event.gameRoomId();
        this.status = GameSettlementOutboxStatus.INIT;
        this.nextAttemptAt = now;
    }

    public static GameSettlementOutbox create(GameFinishedEvent event, LocalDateTime now) {
        return new GameSettlementOutbox(event, now);
    }
}
