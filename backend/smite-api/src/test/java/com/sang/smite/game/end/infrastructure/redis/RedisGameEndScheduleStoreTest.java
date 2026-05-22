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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("registerEndDeadline - naturalDeathAt을 score로 game end pending ZSET에 등록한다")
    void registerEndDeadline() {
        // given
        GameEndDeadlineRegistration registration = new GameEndDeadlineRegistration(
                100L,
                9_000L
        );
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // when
        store.registerEndDeadline(registration);

        // then
        verify(zSetOperations).add(GameEndConstants.GAME_END_PENDING_KEY, "100", 9_000L);
    }

    @Test
    @DisplayName("advanceEndDeadlineIfEarlier - Lua script로 더 빠른 naturalDeathAt 갱신을 원자 처리한다")
    void advanceEndDeadlineIfEarlier() {
        // when
        store.advanceEndDeadlineIfEarlier(100L, 8_000L);

        // then
        verify(stringRedisTemplate).execute(
                any(),
                eq(List.of(GameEndConstants.GAME_END_PENDING_KEY)),
                eq("100"),
                eq("8000")
        );
    }

    @Test
    @DisplayName("findDueEndDeadlines - naturalDeathAt이 지난 gameRoomId를 batch size만큼 조회한다")
    void findDueEndDeadlines() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(
                GameEndConstants.GAME_END_PENDING_KEY,
                0,
                10_000L,
                0,
                2
        )).thenReturn(Set.of("100", "101"));

        // when
        List<Long> dueGameRoomIds = store.findDueEndDeadlines(10_000L, 2);

        // then
        assertThat(dueGameRoomIds).containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    @DisplayName("findDueEndDeadlines - 잘못된 member는 무시하고 pending에서 제거한다")
    void findDueEndDeadlines_InvalidMember() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(
                GameEndConstants.GAME_END_PENDING_KEY,
                0,
                10_000L,
                0,
                2
        )).thenReturn(Set.of("100", "invalid"));

        // when
        List<Long> dueGameRoomIds = store.findDueEndDeadlines(10_000L, 2);

        // then
        assertThat(dueGameRoomIds).containsExactly(100L);
        verify(zSetOperations).remove(GameEndConstants.GAME_END_PENDING_KEY, "invalid");
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
