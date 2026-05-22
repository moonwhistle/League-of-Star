package com.sang.smite.game.end.service;

import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;
import com.sang.smite.game.end.repository.GameEndScheduleStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameEndScheduleService {

    private final GameEndScheduleStore gameEndScheduleStore;

    public GameEndDeadlineRegistration registerEndDeadline(Long gameRoomId, long startAtMillis, long durationMs) {
        long naturalDeathAtMillis = startAtMillis + durationMs;
        GameEndDeadlineRegistration registration = new GameEndDeadlineRegistration(
                gameRoomId,
                naturalDeathAtMillis
        );
        gameEndScheduleStore.registerEndDeadline(registration);
        return registration;
    }

    public void advanceEndDeadlineIfEarlier(Long gameRoomId, long naturalDeathAtMillis) {
        gameEndScheduleStore.advanceEndDeadlineIfEarlier(gameRoomId, naturalDeathAtMillis);
    }

    public void updateEndDeadlineIfDue(Long gameRoomId, long nowMillis, long naturalDeathAtMillis) {
        gameEndScheduleStore.updateEndDeadlineIfDue(gameRoomId, nowMillis, naturalDeathAtMillis);
    }

    public List<Long> findDueEndDeadlines(long nowMillis, int batchSize) {
        return gameEndScheduleStore.findDueEndDeadlines(nowMillis, batchSize);
    }

    public void cleanupEndDeadline(Long gameRoomId) {
        gameEndScheduleStore.cleanupEndDeadline(gameRoomId);
    }
}
