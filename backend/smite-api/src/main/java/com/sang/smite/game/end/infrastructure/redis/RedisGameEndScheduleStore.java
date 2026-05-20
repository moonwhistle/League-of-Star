package com.sang.smite.game.end.infrastructure.redis;

import com.sang.smite.game.end.common.constant.GameEndConstants;
import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;
import com.sang.smite.game.end.repository.GameEndScheduleStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisGameEndScheduleStore implements GameEndScheduleStore {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void registerEndDeadline(GameEndDeadlineRegistration registration) {
        stringRedisTemplate.opsForZSet().add(
                GameEndConstants.GAME_END_PENDING_KEY,
                String.valueOf(registration.gameRoomId()),
                registration.settlementDueAtMillis()
        );
    }
}
