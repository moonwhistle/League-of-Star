package com.sang.smite.game.rtt.infrastructure.redis;

import com.sang.smite.game.rtt.common.constant.GameRttConstants;
import com.sang.smite.game.rtt.domain.GameRttStatus;
import com.sang.smite.game.rtt.repository.GameRttMeasurementStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisGameRttMeasurementStore implements GameRttMeasurementStore {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean initializeIfAbsent(Long gameRoomId, Long userAId, Long userBId) {
        String rttKey = rttKey(gameRoomId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(rttKey))) {
            return false;
        }

        stringRedisTemplate.opsForHash().putAll(rttKey, Map.of(
                GameRttConstants.USER_A_ID_FIELD, String.valueOf(userAId),
                GameRttConstants.USER_B_ID_FIELD, String.valueOf(userBId),
                GameRttConstants.USER_A_SAMPLES_FIELD, "",
                GameRttConstants.USER_B_SAMPLES_FIELD, "",
                GameRttConstants.USER_A_STATUS_FIELD, GameRttStatus.PENDING.name(),
                GameRttConstants.USER_B_STATUS_FIELD, GameRttStatus.PENDING.name()
        ));
        stringRedisTemplate.expire(rttKey, GameRttConstants.RTT_STATE_TTL_SECONDS, TimeUnit.SECONDS);
        return true;
    }

    private String rttKey(Long gameRoomId) {
        return GameRttConstants.RTT_KEY_PREFIX + gameRoomId;
    }
}
