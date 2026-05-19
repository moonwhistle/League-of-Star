package com.sang.smite.game.rtt.infrastructure.redis;

import com.sang.smite.game.rtt.common.constant.GameRttConstants;
import com.sang.smite.game.rtt.domain.GameRttStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGameRttMeasurementStoreTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;
    private static final String RTT_KEY = "game:rtt:100";

    @InjectMocks
    private RedisGameRttMeasurementStore store;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("initializeIfAbsent - RTT HASH 초기 상태를 저장하고 TTL을 설정한다")
    void initializeIfAbsent() {
        // given
        when(stringRedisTemplate.hasKey(RTT_KEY)).thenReturn(false);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        // when
        boolean initialized = store.initializeIfAbsent(GAME_ROOM_ID, USER_A_ID, USER_B_ID);

        // then
        assertThat(initialized).isTrue();
        ArgumentCaptor<Map<Object, Object>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(RTT_KEY), hashCaptor.capture());
        assertThat(hashCaptor.getValue())
                .containsEntry(GameRttConstants.USER_A_ID_FIELD, "1")
                .containsEntry(GameRttConstants.USER_B_ID_FIELD, "2")
                .containsEntry(GameRttConstants.USER_A_SAMPLES_FIELD, "")
                .containsEntry(GameRttConstants.USER_B_SAMPLES_FIELD, "")
                .containsEntry(GameRttConstants.USER_A_STATUS_FIELD, GameRttStatus.PENDING.name())
                .containsEntry(GameRttConstants.USER_B_STATUS_FIELD, GameRttStatus.PENDING.name());
        verify(stringRedisTemplate).expire(RTT_KEY, GameRttConstants.RTT_STATE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("initializeIfAbsent - 기존 RTT HASH가 있으면 덮어쓰지 않는다")
    void initializeIfAbsent_AlreadyExists() {
        // given
        when(stringRedisTemplate.hasKey(RTT_KEY)).thenReturn(true);

        // when
        boolean initialized = store.initializeIfAbsent(GAME_ROOM_ID, USER_A_ID, USER_B_ID);

        // then
        assertThat(initialized).isFalse();
        verify(stringRedisTemplate, never()).opsForHash();
        verify(stringRedisTemplate, never()).expire(RTT_KEY, GameRttConstants.RTT_STATE_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
