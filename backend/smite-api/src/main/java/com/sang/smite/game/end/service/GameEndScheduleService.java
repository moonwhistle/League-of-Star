package com.sang.smite.game.end.service;

import com.sang.smite.game.end.common.constant.GameEndConstants;
import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;
import com.sang.smite.game.end.repository.GameEndScheduleStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameEndScheduleService {

    private final GameEndScheduleStore gameEndScheduleStore;

    public GameEndDeadlineRegistration registerEndDeadline(Long gameRoomId, long startAtMillis, long durationMs) {
        long gameEndAtMillis = startAtMillis + durationMs;
        long settlementDueAtMillis = gameEndAtMillis + GameEndConstants.INPUT_GRACE_MILLIS;
        GameEndDeadlineRegistration registration = new GameEndDeadlineRegistration(
                gameRoomId,
                gameEndAtMillis,
                settlementDueAtMillis
        );
        gameEndScheduleStore.registerEndDeadline(registration);
        return registration;
    }

    public void cleanupEndDeadline(Long gameRoomId) {
        gameEndScheduleStore.cleanupEndDeadline(gameRoomId);
    }
}
