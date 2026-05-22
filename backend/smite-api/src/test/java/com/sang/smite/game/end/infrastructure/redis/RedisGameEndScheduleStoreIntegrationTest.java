package com.sang.smite.game.end.infrastructure.redis;

import com.sang.smite.game.end.common.constant.GameEndConstants;
import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;
import com.sang.smite.redis.AbstractRedisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisGameEndScheduleStoreIntegrationTest extends AbstractRedisTest {

    private static final Long GAME_ROOM_ID = 100L;

    @Autowired
    private RedisGameEndScheduleStore store;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void tearDown() {
        RedisConnection connection = Objects.requireNonNull(stringRedisTemplate.getConnectionFactory())
                .getConnection();
        try {
            connection.serverCommands().flushAll();
        } finally {
            connection.close();
        }
    }

    @Test
    @DisplayName("registerEndDeadline - 실제 Redis에 naturalDeathAt score를 저장한다")
    void registerEndDeadline() {
        // when
        store.registerEndDeadline(new GameEndDeadlineRegistration(GAME_ROOM_ID, 9_000L));

        // then
        assertThat(scoreOf(GAME_ROOM_ID)).isEqualTo(9_000D);
    }

    @Test
    @DisplayName("advanceEndDeadlineIfEarlier - 기존 score보다 빠른 naturalDeathAt만 원자 갱신한다")
    void advanceEndDeadlineIfEarlier() {
        // given
        store.registerEndDeadline(new GameEndDeadlineRegistration(GAME_ROOM_ID, 9_000L));

        // when
        store.advanceEndDeadlineIfEarlier(GAME_ROOM_ID, 8_000L);
        store.advanceEndDeadlineIfEarlier(GAME_ROOM_ID, 8_500L);

        // then
        assertThat(scoreOf(GAME_ROOM_ID)).isEqualTo(8_000D);
    }

    @Test
    @DisplayName("updateEndDeadlineIfDue - 현재 score가 due 상태일 때만 늦은 naturalDeathAt으로 갱신한다")
    void updateEndDeadlineIfDue() {
        // given
        store.registerEndDeadline(new GameEndDeadlineRegistration(GAME_ROOM_ID, 9_000L));

        // when
        store.updateEndDeadlineIfDue(GAME_ROOM_ID, 9_000L, 11_000L);
        store.advanceEndDeadlineIfEarlier(GAME_ROOM_ID, 10_000L);
        store.updateEndDeadlineIfDue(GAME_ROOM_ID, 9_000L, 12_000L);

        // then
        assertThat(scoreOf(GAME_ROOM_ID)).isEqualTo(10_000D);
    }

    @Test
    @DisplayName("findDueEndDeadlines - 실제 Redis에서 due gameRoomId만 조회한다")
    void findDueEndDeadlines() {
        // given
        store.registerEndDeadline(new GameEndDeadlineRegistration(100L, 9_000L));
        store.registerEndDeadline(new GameEndDeadlineRegistration(101L, 10_000L));
        store.registerEndDeadline(new GameEndDeadlineRegistration(102L, 11_000L));

        // when
        List<Long> result = store.findDueEndDeadlines(10_000L, 10);

        // then
        assertThat(result).containsExactly(100L, 101L);
    }

    private Double scoreOf(Long gameRoomId) {
        return stringRedisTemplate.opsForZSet().score(
                GameEndConstants.GAME_END_PENDING_KEY,
                String.valueOf(gameRoomId)
        );
    }
}
