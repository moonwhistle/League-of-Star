package com.sang.smite.game.waiting.service;

import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.repository.GameWaitingStore;
import com.sang.smite.redis.lock.annotation.DistributedRedisLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * WebSocket CLIENT_READY를 gameRoom waiting Redis 상태에 반영합니다.
 */
@Service
@RequiredArgsConstructor
public class GameWaitingReadyService {

    private final GameWaitingStore gameWaitingStore;

    @DistributedRedisLock(key = "'" + GameWaitingConstants.WAITING_TIMEOUT_LOCK_KEY_PREFIX + "' + #gameRoomId")
    public GameWaitingReadyResult markReady(Long gameRoomId, Long userId) {
        return gameWaitingStore.markReady(gameRoomId, userId);
    }
}
