package com.sang.smite.game.waiting.infrastructure.redis;

import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.domain.GameWaitingTimeoutRegistration;
import com.sang.smite.game.waiting.repository.GameWaitingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Redis ZSET/HASH 기반 gameRoom waiting timeout 저장소입니다.
 */
@Repository
@RequiredArgsConstructor
public class RedisGameWaitingStore implements GameWaitingStore {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void registerWaitingTimeout(GameWaitingTimeoutRegistration registration) {
        long createdAtMillis = toEpochMillis(registration.createdAt());
        long deadlineAtMillis = toEpochMillis(registration.deadlineAt());
        String gameRoomId = String.valueOf(registration.gameRoomId());
        String waitingKey = waitingKey(registration.gameRoomId());

        stringRedisTemplate.opsForHash().putAll(waitingKey, Map.of(
                GameWaitingConstants.USER_A_ID_FIELD, String.valueOf(registration.userAId()),
                GameWaitingConstants.USER_B_ID_FIELD, String.valueOf(registration.userBId()),
                GameWaitingConstants.USER_A_READY_FIELD, Boolean.FALSE.toString(),
                GameWaitingConstants.USER_B_READY_FIELD, Boolean.FALSE.toString(),
                GameWaitingConstants.CREATED_AT_MILLIS_FIELD, String.valueOf(createdAtMillis),
                GameWaitingConstants.DEADLINE_AT_MILLIS_FIELD, String.valueOf(deadlineAtMillis)
        ));
        stringRedisTemplate.expire(waitingKey, GameWaitingConstants.WAITING_STATE_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForZSet().add(
                GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY,
                gameRoomId,
                deadlineAtMillis
        );
    }

    @Override
    public GameWaitingReadyResult markReady(Long gameRoomId, Long userId) {
        String waitingKey = waitingKey(gameRoomId);
        Map<Object, Object> waitingState = stringRedisTemplate.opsForHash().entries(waitingKey);
        if (waitingState.isEmpty()) {
            return GameWaitingReadyResult.rejected();
        }

        String readyField = resolveReadyField(waitingState, userId);
        if (readyField == null) {
            return GameWaitingReadyResult.rejected();
        }

        stringRedisTemplate.opsForHash().put(waitingKey, readyField, Boolean.TRUE.toString());
        Map<Object, Object> updatedWaitingState = stringRedisTemplate.opsForHash().entries(waitingKey);
        if (updatedWaitingState.isEmpty()) {
            return GameWaitingReadyResult.rejected();
        }

        boolean bothReady = isReady(updatedWaitingState, GameWaitingConstants.USER_A_READY_FIELD)
                && isReady(updatedWaitingState, GameWaitingConstants.USER_B_READY_FIELD);
        if (bothReady) {
            cleanup(gameRoomId);
        }

        return GameWaitingReadyResult.accepted(bothReady);
    }

    @Override
    public void cleanup(Long gameRoomId) {
        String gameRoomIdValue = String.valueOf(gameRoomId);
        stringRedisTemplate.delete(waitingKey(gameRoomId));
        stringRedisTemplate.opsForZSet().remove(GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY, gameRoomIdValue);
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String waitingKey(Long gameRoomId) {
        return GameWaitingConstants.WAITING_KEY_PREFIX + gameRoomId;
    }

    private String resolveReadyField(Map<Object, Object> waitingState, Long userId) {
        String userIdValue = String.valueOf(userId);
        if (Objects.equals(waitingState.get(GameWaitingConstants.USER_A_ID_FIELD), userIdValue)) {
            return GameWaitingConstants.USER_A_READY_FIELD;
        }
        if (Objects.equals(waitingState.get(GameWaitingConstants.USER_B_ID_FIELD), userIdValue)) {
            return GameWaitingConstants.USER_B_READY_FIELD;
        }
        return null;
    }

    private boolean isReady(Map<Object, Object> waitingState, String fieldName) {
        return Boolean.parseBoolean((String) waitingState.get(fieldName));
    }
}
