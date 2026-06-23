package com.sang.leagueofstar.domain.customgame.domain;

import com.sang.leagueofstar.common.domain.BaseEntity;
import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "custom_game_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_custom_game_rooms_invite_code", columnNames = "invite_code")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomRoomStatus status = CustomRoomStatus.WAITING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "custom_room_id")
    private List<CustomGameParticipant> participants = new ArrayList<>();

    public static CustomGameRoom create(Long ownerUserId, String inviteCode) {
        validateUserId(ownerUserId);
        validateInviteCode(inviteCode);

        CustomGameRoom customGameRoom = CustomGameRoom.builder()
                .ownerUserId(ownerUserId)
                .inviteCode(inviteCode)
                .build();
        customGameRoom.addOwnerParticipant(ownerUserId);
        return customGameRoom;
    }

    public void addPlayerParticipant(Long userId) {
        addParticipant(userId, CustomRoomParticipantRole.PLAYER);
    }

    public boolean hasParticipant(Long userId) {
        return participants.stream()
                .anyMatch(participant -> Objects.equals(participant.getUserId(), userId));
    }

    public int currentParticipantCount() {
        return participants.size();
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
        this.startedAt = startedAt;
    }

    public void close(LocalDateTime closedAt) {
        if (isClosed()) {
            return;
        }
        this.status = CustomRoomStatus.CLOSED;
        this.closedAt = closedAt;
    }

    private void addOwnerParticipant(Long ownerUserId) {
        addParticipant(ownerUserId, CustomRoomParticipantRole.OWNER);
    }

    private void addParticipant(Long userId, CustomRoomParticipantRole role) {
        validateUserId(userId);
        if (!isWaiting()) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE);
        }
        if (participants.size() >= MAX_PARTICIPANTS) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_FULL);
        }
        if (hasParticipant(userId)) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_DUPLICATE_PARTICIPANT);
        }
        participants.add(CustomGameParticipant.create(userId, role));
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
