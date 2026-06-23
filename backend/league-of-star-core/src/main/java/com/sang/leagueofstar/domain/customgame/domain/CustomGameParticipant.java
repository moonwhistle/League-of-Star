package com.sang.leagueofstar.domain.customgame.domain;

import com.sang.leagueofstar.common.domain.BaseEntity;
import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import jakarta.persistence.Column;
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

@Entity
@Table(name = "custom_game_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CustomGameParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomRoomParticipantRole role;

    public static CustomGameParticipant create(Long userId, CustomRoomParticipantRole role) {
        if (userId == null) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT);
        }
        if (role == null) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT);
        }
        return CustomGameParticipant.builder()
                .userId(userId)
                .role(role)
                .build();
    }
}
