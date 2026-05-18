package com.sang.smite.game.waiting.repository;

import com.sang.smite.game.waiting.domain.GameWaitingTimeoutRegistration;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;

/**
 * gameRoom waiting timeout 상태 저장소입니다.
 */
public interface GameWaitingStore {

    void registerWaitingTimeout(GameWaitingTimeoutRegistration registration);

    GameWaitingReadyResult markReady(Long gameRoomId, Long userId);

    void cleanup(Long gameRoomId);
}
