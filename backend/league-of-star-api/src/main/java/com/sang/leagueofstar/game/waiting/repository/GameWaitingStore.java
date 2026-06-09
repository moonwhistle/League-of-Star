package com.sang.leagueofstar.game.waiting.repository;

import com.sang.leagueofstar.game.waiting.domain.GameWaitingTimeoutRegistration;
import com.sang.leagueofstar.game.waiting.domain.GameWaitingReadyResult;
import com.sang.leagueofstar.game.waiting.domain.GameWaitingState;

import java.util.List;
import java.util.Optional;

/**
 * gameRoom waiting timeout 상태 저장소입니다.
 */
public interface GameWaitingStore {

    void registerWaitingTimeout(GameWaitingTimeoutRegistration registration);

    GameWaitingReadyResult markReady(Long gameRoomId, Long userId);

    List<Long> findDueTimeouts(long nowMillis, int batchSize);

    Optional<GameWaitingState> findWaitingState(Long gameRoomId);

    void cleanup(Long gameRoomId);
}
