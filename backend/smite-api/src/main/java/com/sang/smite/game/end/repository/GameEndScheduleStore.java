package com.sang.smite.game.end.repository;

import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;

import java.util.List;

public interface GameEndScheduleStore {

    void registerEndDeadline(GameEndDeadlineRegistration registration);

    void advanceEndDeadlineIfEarlier(Long gameRoomId, long naturalDeathAtMillis);

    List<Long> findDueEndDeadlines(long nowMillis, int batchSize);

    void cleanupEndDeadline(Long gameRoomId);
}
