package com.sang.leagueofstar.domain.customgame.domain;

import com.sang.leagueofstar.common.domain.BaseEntity;
import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
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

import java.time.LocalDateTime;

@Entity
@Table(
        name = "custom_game_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_custom_game_rooms_invite_code", columnNames = "invite_code"),
                @UniqueConstraint(name = "uk_custom_game_rooms_waiting_owner", columnNames = "waiting_owner_user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CustomGameRoom extends BaseEntity {

    public static final int MAX_PARTICIPANTS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invite_code", nullable = false, length = 16)
    private String inviteCode;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "waiting_owner_user_id")
    private Long waitingOwnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomRoomStatus status = CustomRoomStatus.WAITING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public static CustomGameRoom create(Long ownerUserId, String inviteCode) {
        validateUserId(ownerUserId);
        validateInviteCode(inviteCode);

        return CustomGameRoom.builder()
                .ownerUserId(ownerUserId)
                .waitingOwnerUserId(ownerUserId)
                .inviteCode(inviteCode)
                .build();
    }

    public boolean isWaiting() {
        return status.isWaiting();
    }

    public boolean isStarted() {
        return status.isStarted();
    }

    public boolean isClosed() {
        return status.isClosed();
    }

    public void markStarted(LocalDateTime startedAt) {
        if (!isWaiting()) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE);
        }
        this.status = CustomRoomStatus.STARTED;
        this.waitingOwnerUserId = null;
        this.startedAt = startedAt;
    }

    public void close(LocalDateTime closedAt) {
        if (isClosed()) {
            return;
        }
        this.status = CustomRoomStatus.CLOSED;
        this.waitingOwnerUserId = null;
        this.closedAt = closedAt;
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT);
        }
    }

    private static void validateInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_INVITE_CODE);
        }
    }
}
