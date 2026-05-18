package com.sang.smite.game.waiting.infrastructure.redis;

import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;
import com.sang.smite.game.waiting.domain.GameWaitingTimeoutRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGameWaitingStoreTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 18, 10, 0);
    private static final String WAITING_KEY = "game:waiting:100";

    @InjectMocks
    private RedisGameWaitingStore store;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("registerWaitingTimeout - waiting HASH와 timeout pending ZSET을 저장하고 HASH TTL을 설정한다")
    void registerWaitingTimeout() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        GameWaitingTimeoutRegistration registration = new GameWaitingTimeoutRegistration(
                GAME_ROOM_ID,
                USER_A_ID,
                USER_B_ID,
                CREATED_AT
        );
        long createdAtMillis = toEpochMillis(CREATED_AT);
        long deadlineAtMillis = toEpochMillis(CREATED_AT.plusSeconds(GameWaitingConstants.WAITING_TIMEOUT_SECONDS));

        // when
        store.registerWaitingTimeout(registration);

        // then
        ArgumentCaptor<Map<Object, Object>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(WAITING_KEY), hashCaptor.capture());
        assertThat(hashCaptor.getValue()).containsEntry(GameWaitingConstants.USER_A_ID_FIELD, "1")
                .containsEntry(GameWaitingConstants.USER_B_ID_FIELD, "2")
                .containsEntry(GameWaitingConstants.USER_A_READY_FIELD, "false")
                .containsEntry(GameWaitingConstants.USER_B_READY_FIELD, "false")
                .containsEntry(GameWaitingConstants.CREATED_AT_MILLIS_FIELD, String.valueOf(createdAtMillis))
                .containsEntry(GameWaitingConstants.DEADLINE_AT_MILLIS_FIELD, String.valueOf(deadlineAtMillis));

        verify(stringRedisTemplate).expire(
                WAITING_KEY,
                GameWaitingConstants.WAITING_STATE_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        verify(zSetOperations).add(
                GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY,
                String.valueOf(GAME_ROOM_ID),
                deadlineAtMillis
        );
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
