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

import java.util.HashMap;
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

    @Test
    @DisplayName("appendSample - 5개 미만이면 samples에 RTT를 append한다")
    void appendSample_Recorded() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RTT_KEY)).thenReturn(rttState("10,20", GameRttStatus.PENDING));

        // when
        var result = store.appendSample(GAME_ROOM_ID, USER_A_ID, 30L);

        // then
        assertThat(result.accepted()).isTrue();
        assertThat(result.completed()).isFalse();
        assertThat(result.sampleCount()).isEqualTo(3);
        verify(hashOperations).put(RTT_KEY, GameRttConstants.USER_A_SAMPLES_FIELD, "10,20,30");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("appendSample - 5개가 모이면 median RTT를 저장하고 PASSED로 전환한다")
    void appendSample_CompletedPassed() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RTT_KEY)).thenReturn(rttState("40,10,30,20", GameRttStatus.PENDING));

        // when
        var result = store.appendSample(GAME_ROOM_ID, USER_A_ID, 50L);

        // then
        assertThat(result.accepted()).isTrue();
        assertThat(result.completed()).isTrue();
        assertThat(result.passed()).isTrue();
        ArgumentCaptor<Map<Object, Object>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(RTT_KEY), hashCaptor.capture());
        assertThat(hashCaptor.getValue())
                .containsEntry(GameRttConstants.USER_A_SAMPLES_FIELD, "40,10,30,20,50")
                .containsEntry(GameRttConstants.USER_A_MEDIAN_RTT_MS_FIELD, "30")
                .containsEntry(GameRttConstants.USER_A_STATUS_FIELD, GameRttStatus.PASSED.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("appendSample - median RTT가 기준을 넘으면 FAILED로 전환한다")
    void appendSample_CompletedFailed() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RTT_KEY)).thenReturn(rttState("2100,2200,2300,2400", GameRttStatus.PENDING));

        // when
        var result = store.appendSample(GAME_ROOM_ID, USER_A_ID, 2500L);

        // then
        assertThat(result.accepted()).isTrue();
        assertThat(result.completed()).isTrue();
        assertThat(result.passed()).isFalse();
        ArgumentCaptor<Map<Object, Object>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(RTT_KEY), hashCaptor.capture());
        assertThat(hashCaptor.getValue())
                .containsEntry(GameRttConstants.USER_A_MEDIAN_RTT_MS_FIELD, "2300")
                .containsEntry(GameRttConstants.USER_A_STATUS_FIELD, GameRttStatus.FAILED.name());
    }

    @Test
    @DisplayName("appendSample - RTT HASH가 없으면 rejected를 반환한다")
    void appendSample_NotFound() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RTT_KEY)).thenReturn(Map.of());

        // when
        var result = store.appendSample(GAME_ROOM_ID, USER_A_ID, 30L);

        // then
        assertThat(result.accepted()).isFalse();
        verify(hashOperations, never()).put(eq(RTT_KEY), eq(GameRttConstants.USER_A_SAMPLES_FIELD), eq("30"));
    }

    @Test
    @DisplayName("markFailed - PENDING 유저의 RTT 상태를 FAILED로 전환한다")
    void markFailed() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RTT_KEY)).thenReturn(rttState("", GameRttStatus.PENDING));

        // when
        boolean failed = store.markFailed(GAME_ROOM_ID, USER_A_ID);

        // then
        assertThat(failed).isTrue();
        verify(hashOperations).put(RTT_KEY, GameRttConstants.USER_A_STATUS_FIELD, GameRttStatus.FAILED.name());
    }

    @Test
    @DisplayName("markFailed - 이미 완료된 유저는 no-op 처리한다")
    void markFailed_NotPending() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RTT_KEY)).thenReturn(rttState("", GameRttStatus.PASSED));

        // when
        boolean failed = store.markFailed(GAME_ROOM_ID, USER_A_ID);

        // then
        assertThat(failed).isFalse();
        verify(hashOperations, never()).put(RTT_KEY, GameRttConstants.USER_A_STATUS_FIELD, GameRttStatus.FAILED.name());
    }

    private Map<Object, Object> rttState(String userASamples, GameRttStatus userAStatus) {
        Map<Object, Object> state = new HashMap<>();
        state.put(GameRttConstants.USER_A_ID_FIELD, String.valueOf(USER_A_ID));
        state.put(GameRttConstants.USER_B_ID_FIELD, String.valueOf(USER_B_ID));
        state.put(GameRttConstants.USER_A_SAMPLES_FIELD, userASamples);
        state.put(GameRttConstants.USER_B_SAMPLES_FIELD, "");
        state.put(GameRttConstants.USER_A_STATUS_FIELD, userAStatus.name());
        state.put(GameRttConstants.USER_B_STATUS_FIELD, GameRttStatus.PENDING.name());
        return state;
    }
}
