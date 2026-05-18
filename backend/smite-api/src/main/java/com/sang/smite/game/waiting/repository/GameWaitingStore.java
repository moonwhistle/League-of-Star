package com.sang.smite.game.waiting.repository;

import com.sang.smite.game.waiting.domain.GameWaitingTimeoutRegistration;

/**
 * gameRoom waiting timeout 상태 저장소입니다.
 */
public interface GameWaitingStore {

    void registerWaitingTimeout(GameWaitingTimeoutRegistration registration);
}
