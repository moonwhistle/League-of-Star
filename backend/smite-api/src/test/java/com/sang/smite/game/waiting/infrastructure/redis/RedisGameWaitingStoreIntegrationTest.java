package com.sang.smite.game.waiting.infrastructure.redis;

import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.domain.GameWaitingState;
import com.sang.smite.game.waiting.domain.GameWaitingTimeoutRegistration;
import com.sang.smite.redis.AbstractRedisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisGameWaitingStoreIntegrationTest extends AbstractRedisTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 18, 10, 0);
    private static final String WAITING_KEY = "game:waiting:100";

    @Autowired
    private RedisGameWaitingStore store;

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
    @DisplayName("registerWaitingTimeout - 실제 Redis에 waiting HASH, timeout ZSET, HASH TTL을 저장한다")
    void registerWaitingTimeout() {
        // given
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
        Map<Object, Object> waitingState = stringRedisTemplate.opsForHash().entries(WAITING_KEY);
        assertThat(waitingState).containsEntry(GameWaitingConstants.USER_A_ID_FIELD, "1")
                .containsEntry(GameWaitingConstants.USER_B_ID_FIELD, "2")
                .containsEntry(GameWaitingConstants.USER_A_READY_FIELD, "false")
                .containsEntry(GameWaitingConstants.USER_B_READY_FIELD, "false")
                .containsEntry(GameWaitingConstants.CREATED_AT_MILLIS_FIELD, String.valueOf(createdAtMillis))
                .containsEntry(GameWaitingConstants.DEADLINE_AT_MILLIS_FIELD, String.valueOf(deadlineAtMillis));

        Long ttlSeconds = stringRedisTemplate.getExpire(WAITING_KEY, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(GameWaitingConstants.WAITING_STATE_TTL_SECONDS);

        Set<String> pendingGameRoomIds = stringRedisTemplate.opsForZSet().rangeByScore(
                GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY,
                deadlineAtMillis,
                deadlineAtMillis
        );
        assertThat(pendingGameRoomIds).containsExactly(String.valueOf(GAME_ROOM_ID));
        assertThat(stringRedisTemplate.opsForZSet().score(
                GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY,
                String.valueOf(GAME_ROOM_ID)
        )).isEqualTo((double) deadlineAtMillis);
    }

    @Test
    @DisplayName("markReady - 실제 Redis HASH ready 값을 갱신하고 양쪽 READY 완료 시 cleanup한다")
    void markReady() {
        // given
        store.registerWaitingTimeout(new GameWaitingTimeoutRegistration(
                GAME_ROOM_ID,
                USER_A_ID,
                USER_B_ID,
                CREATED_AT
        ));

        // when
        GameWaitingReadyResult firstResult = store.markReady(GAME_ROOM_ID, USER_A_ID);
        GameWaitingReadyResult secondResult = store.markReady(GAME_ROOM_ID, USER_B_ID);

        // then
        assertThat(firstResult.accepted()).isTrue();
        assertThat(firstResult.bothReady()).isFalse();
        assertThat(secondResult.accepted()).isTrue();
        assertThat(secondResult.bothReady()).isTrue();
        assertThat(stringRedisTemplate.hasKey(WAITING_KEY)).isFalse();
        assertThat(stringRedisTemplate.opsForZSet().score(
                GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY,
                String.valueOf(GAME_ROOM_ID)
        )).isNull();
    }

    @Test
    @DisplayName("findDueTimeouts/findWaitingState - 실제 Redis에서 due timeout과 waiting state를 조회한다")
    void findDueTimeoutsAndWaitingState() {
        // given
        store.registerWaitingTimeout(new GameWaitingTimeoutRegistration(
                GAME_ROOM_ID,
                USER_A_ID,
                USER_B_ID,
                CREATED_AT
        ));
        long deadlineAtMillis = toEpochMillis(CREATED_AT.plusSeconds(GameWaitingConstants.WAITING_TIMEOUT_SECONDS));

        // when
        java.util.List<Long> beforeDeadline = store.findDueTimeouts(deadlineAtMillis - 1, 10);
        java.util.List<Long> afterDeadline = store.findDueTimeouts(deadlineAtMillis, 10);
        Optional<GameWaitingState> waitingState = store.findWaitingState(GAME_ROOM_ID);

        // then
        assertThat(beforeDeadline).isEmpty();
        assertThat(afterDeadline).containsExactly(GAME_ROOM_ID);
        assertThat(waitingState).isPresent();
        assertThat(waitingState.get().gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(waitingState.get().bothReady()).isFalse();
        assertThat(waitingState.get().deadlineAtMillis()).isEqualTo(deadlineAtMillis);
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
