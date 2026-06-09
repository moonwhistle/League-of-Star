package com.sang.leagueofstar.game.waiting.service;

import com.sang.leagueofstar.game.waiting.common.constant.GameWaitingConstants;
import com.sang.leagueofstar.game.waiting.repository.GameWaitingStore;
import com.sang.leagueofstar.redis.lock.exception.RedisLockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * gameRoom waiting timeout batch 처리 흐름을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameWaitingTimeoutService {

    private final GameWaitingStore gameWaitingStore;
    private final GameWaitingTimeoutProcessor gameWaitingTimeoutProcessor;
    private final Clock clock;

    public void processTimeouts() {
        long nowMillis = clock.millis();
        List<Long> dueGameRoomIds = gameWaitingStore.findDueTimeouts(
                nowMillis,
                GameWaitingConstants.WAITING_TIMEOUT_CANDIDATE_BATCH_SIZE
        );

        for (Long gameRoomId : dueGameRoomIds) {
            processGameRoomTimeout(gameRoomId);
        }
    }

    private void processGameRoomTimeout(Long gameRoomId) {
        try {
            gameWaitingTimeoutProcessor.processTimeoutWithLock(gameRoomId);
        } catch (RedisLockAcquisitionException e) {
            log.debug("Skip game waiting timeout because lock is held: gameRoomId={}", gameRoomId);
        } catch (Exception e) {
            log.warn("Failed to process game waiting timeout: gameRoomId={}", gameRoomId, e);
        }
    }
}
