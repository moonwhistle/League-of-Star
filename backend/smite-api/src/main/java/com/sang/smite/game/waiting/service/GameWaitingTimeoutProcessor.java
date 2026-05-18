package com.sang.smite.game.waiting.service;

import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;
import com.sang.smite.game.waiting.domain.GameWaitingState;
import com.sang.smite.game.waiting.repository.GameWaitingStore;
import com.sang.smite.redis.lock.annotation.DistributedRedisLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 단일 gameRoom waiting timeout 정산을 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class GameWaitingTimeoutProcessor {

    private final GameWaitingStore gameWaitingStore;
    private final GameRoomCommandService gameRoomCommandService;

    @DistributedRedisLock(key = "'" + GameWaitingConstants.WAITING_TIMEOUT_LOCK_KEY_PREFIX + "' + #gameRoomId")
    public void processTimeoutWithLock(Long gameRoomId) {
        Optional<GameWaitingState> waitingState = gameWaitingStore.findWaitingState(gameRoomId);
        if (waitingState.isEmpty()) {
            gameWaitingStore.cleanup(gameRoomId);
            return;
        }

        if (waitingState.get().bothReady()) {
            gameWaitingStore.cleanup(gameRoomId);
            return;
        }

        gameRoomCommandService.abortReadyRoomIfReady(gameRoomId);
        gameWaitingStore.cleanup(gameRoomId);
    }
}
