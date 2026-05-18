package com.sang.smite.game.waiting.infrastructure.redis;

import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.domain.GameWaitingState;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("markReady - userA의 CLIENT_READY를 Redis HASH에 반영한다")
    void markReady_UserAReady() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(WAITING_KEY))
                .thenReturn(waitingState("false", "false"))
                .thenReturn(waitingState("true", "false"));

        // when
        GameWaitingReadyResult result = store.markReady(GAME_ROOM_ID, USER_A_ID);

        // then
        assertThat(result.accepted()).isTrue();
        assertThat(result.bothReady()).isFalse();
        verify(hashOperations).put(WAITING_KEY, GameWaitingConstants.USER_A_READY_FIELD, "true");
        verify(stringRedisTemplate, never()).delete(WAITING_KEY);
    }

    @Test
    @DisplayName("markReady - 양쪽 READY가 완료되면 waiting HASH와 timeout ZSET index를 정리한다")
    void markReady_BothReady_Cleanup() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(hashOperations.entries(WAITING_KEY))
                .thenReturn(waitingState("true", "false"))
                .thenReturn(waitingState("true", "true"));

        // when
        GameWaitingReadyResult result = store.markReady(GAME_ROOM_ID, USER_B_ID);

        // then
        assertThat(result.accepted()).isTrue();
        assertThat(result.bothReady()).isTrue();
        verify(hashOperations).put(WAITING_KEY, GameWaitingConstants.USER_B_READY_FIELD, "true");
        verify(stringRedisTemplate).delete(WAITING_KEY);
        verify(zSetOperations).remove(
                GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY,
                String.valueOf(GAME_ROOM_ID)
        );
    }

    @Test
    @DisplayName("markReady - waiting HASH가 없으면 rejected를 반환한다")
    void markReady_WaitingStateNotFound() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(WAITING_KEY)).thenReturn(Map.of());

        // when
        GameWaitingReadyResult result = store.markReady(GAME_ROOM_ID, USER_A_ID);

        // then
        assertThat(result.accepted()).isFalse();
        verify(hashOperations, never()).put(WAITING_KEY, GameWaitingConstants.USER_A_READY_FIELD, "true");
    }

    @Test
    @DisplayName("markReady - gameRoom 참가자가 아닌 userId이면 rejected를 반환한다")
    void markReady_NotParticipant() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(WAITING_KEY)).thenReturn(waitingState("false", "false"));

        // when
        GameWaitingReadyResult result = store.markReady(GAME_ROOM_ID, 999L);

        // then
        assertThat(result.accepted()).isFalse();
        verify(hashOperations, never()).put(WAITING_KEY, GameWaitingConstants.USER_A_READY_FIELD, "true");
        verify(hashOperations, never()).put(WAITING_KEY, GameWaitingConstants.USER_B_READY_FIELD, "true");
    }

    @Test
    @DisplayName("findDueTimeouts - deadline이 지난 gameRoomId를 batch size만큼 조회한다")
    void findDueTimeouts() {
        // given
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(
                GameWaitingConstants.WAITING_TIMEOUT_PENDING_KEY,
                0,
                10_000L,
                0,
                2
        )).thenReturn(java.util.Set.of("100", "101"));

        // when
        List<Long> dueGameRoomIds = store.findDueTimeouts(10_000L, 2);

        // then
        assertThat(dueGameRoomIds).containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    @DisplayName("findWaitingState - Redis HASH를 GameWaitingState로 변환한다")
    void findWaitingState() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(WAITING_KEY)).thenReturn(waitingState("true", "false"));

        // when
        Optional<GameWaitingState> waitingState = store.findWaitingState(GAME_ROOM_ID);

        // then
        assertThat(waitingState).isPresent();
        assertThat(waitingState.get().gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(waitingState.get().userAId()).isEqualTo(USER_A_ID);
        assertThat(waitingState.get().userBId()).isEqualTo(USER_B_ID);
        assertThat(waitingState.get().userAReady()).isTrue();
        assertThat(waitingState.get().userBReady()).isFalse();
        assertThat(waitingState.get().createdAtMillis()).isEqualTo(1L);
        assertThat(waitingState.get().deadlineAtMillis()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findWaitingState - Redis HASH가 없으면 Optional.empty를 반환한다")
    void findWaitingState_NotFound() {
        // given
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(WAITING_KEY)).thenReturn(Map.of());

        // when
        Optional<GameWaitingState> waitingState = store.findWaitingState(GAME_ROOM_ID);

        // then
        assertThat(waitingState).isEmpty();
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Map<Object, Object> waitingState(String userAReady, String userBReady) {
        return Map.of(
                GameWaitingConstants.USER_A_ID_FIELD, String.valueOf(USER_A_ID),
                GameWaitingConstants.USER_B_ID_FIELD, String.valueOf(USER_B_ID),
                GameWaitingConstants.USER_A_READY_FIELD, userAReady,
                GameWaitingConstants.USER_B_READY_FIELD, userBReady,
                GameWaitingConstants.CREATED_AT_MILLIS_FIELD, "1",
                GameWaitingConstants.DEADLINE_AT_MILLIS_FIELD, "2"
        );
    }
}
