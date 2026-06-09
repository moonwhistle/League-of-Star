package com.sang.leagueofstar.domain.game.domain;

import com.sang.leagueofstar.common.domain.BaseEntity;
import com.sang.leagueofstar.domain.game.domain.vo.ParticipantStatus;
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

/**
 * 게임 방에 참여한 사용자 정보를 관리하는 엔티티입니다.
 * 1v1 대전의 각 참여자를 독립적인 엔티티로 관리하여 확장성을 확보합니다.
 */
@Entity
@Table(name = "game_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.READY;

    public void updateStatus(ParticipantStatus status) {
        this.status = status;
    }
}
