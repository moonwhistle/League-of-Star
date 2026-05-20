package com.sang.smite.game.end.infrastructure.redis;

import com.sang.smite.game.end.common.constant.GameEndConstants;
import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGameEndScheduleStoreTest {

    @InjectMocks
    private RedisGameEndScheduleStore store;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Test
    @DisplayName("registerEndDeadline - settlementDueAt을 score로 game end pending ZSET에 등록한다")
    void registerEndDeadline() {
        // given
        GameEndDeadlineRegistration registration = new GameEndDeadlineRegistration(
                100L,
                9_000L,
                11_000L
        );
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // when
        store.registerEndDeadline(registration);

        // then
        verify(zSetOperations).add(GameEndConstants.GAME_END_PENDING_KEY, "100", 11_000L);
    }

    @Test
    @DisplayName("cleanupEndDeadline - game end pending ZSET에서 gameRoomId를 제거한다")
    void cleanupEndDeadline() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // when
        store.cleanupEndDeadline(100L);

        // then
        verify(zSetOperations).remove(GameEndConstants.GAME_END_PENDING_KEY, "100");
    }
}
