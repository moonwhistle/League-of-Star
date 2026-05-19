package com.sang.smite.game.rtt.repository;

import com.sang.smite.game.rtt.domain.GameRttPongResult;
import com.sang.smite.game.rtt.domain.GameRttState;

import java.util.Optional;

public interface GameRttMeasurementStore {

    boolean initializeIfAbsent(Long gameRoomId, Long userAId, Long userBId);

    GameRttPongResult appendSample(Long gameRoomId, Long userId, long rttMillis);

    boolean markFailed(Long gameRoomId, Long userId);

    Optional<GameRttState> findState(Long gameRoomId);

    void cleanup(Long gameRoomId);
}
