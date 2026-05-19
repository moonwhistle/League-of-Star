package com.sang.smite.game.rtt.repository;

public interface GameRttMeasurementStore {

    boolean initializeIfAbsent(Long gameRoomId, Long userAId, Long userBId);
}
