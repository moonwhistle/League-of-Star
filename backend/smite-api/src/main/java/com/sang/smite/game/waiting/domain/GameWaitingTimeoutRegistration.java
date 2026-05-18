package com.sang.smite.game.waiting.domain;

import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * READY gameRoom의 WebSocket 대기 timeout 등록 요청입니다.
 */
public record GameWaitingTimeoutRegistration(
        Long gameRoomId,
        Long userAId,
        Long userBId,
        LocalDateTime createdAt
) {

    public GameWaitingTimeoutRegistration {
        Objects.requireNonNull(gameRoomId, "gameRoomId must not be null");
        Objects.requireNonNull(userAId, "userAId must not be null");
        Objects.requireNonNull(userBId, "userBId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public LocalDateTime deadlineAt() {
        return createdAt.plusSeconds(GameWaitingConstants.WAITING_TIMEOUT_SECONDS);
    }
}
