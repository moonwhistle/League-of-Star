package com.sang.leagueofstar.game.end.repository;

import com.sang.leagueofstar.game.end.domain.GameEndDeadlineRegistration;

import java.util.List;

public interface GameEndScheduleStore {

    void registerEndDeadline(GameEndDeadlineRegistration registration);

    void advanceEndDeadlineIfEarlier(Long gameRoomId, long naturalDeathAtMillis);

    void updateEndDeadlineIfDue(Long gameRoomId, long nowMillis, long naturalDeathAtMillis);

    List<Long> findDueEndDeadlines(long nowMillis, int batchSize);

    void cleanupEndDeadline(Long gameRoomId);
}
