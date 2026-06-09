package com.sang.leagueofstar.game.waiting.service;

import com.sang.leagueofstar.game.waiting.common.constant.GameWaitingConstants;
import com.sang.leagueofstar.game.waiting.repository.GameWaitingStore;
import com.sang.leagueofstar.redis.lock.exception.RedisLockAcquisitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameWaitingTimeoutServiceTest {

    @InjectMocks
    private GameWaitingTimeoutService service;

    @Mock
    private GameWaitingStore gameWaitingStore;

    @Mock
    private GameWaitingTimeoutProcessor gameWaitingTimeoutProcessor;

    @Mock
    private Clock clock;

    @Test
    @DisplayName("due gameRoomId 목록을 조회하고 각각 timeout 처리를 시도한다")
    void processTimeouts() {
        // given
        when(clock.millis()).thenReturn(10_000L);
        when(gameWaitingStore.findDueTimeouts(
                10_000L,
                GameWaitingConstants.WAITING_TIMEOUT_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(100L, 101L));

        // when
        service.processTimeouts();

        // then
        verify(gameWaitingTimeoutProcessor).processTimeoutWithLock(100L);
        verify(gameWaitingTimeoutProcessor).processTimeoutWithLock(101L);
    }

    @Test
    @DisplayName("lock 획득 실패는 해당 gameRoom만 skip하고 다음 gameRoom 처리를 계속한다")
    void processTimeouts_LockFailure_ContinueNext() {
        // given
        when(clock.millis()).thenReturn(10_000L);
        when(gameWaitingStore.findDueTimeouts(
                10_000L,
                GameWaitingConstants.WAITING_TIMEOUT_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(100L, 101L));
        doThrow(new RedisLockAcquisitionException("game:waiting:timeout:lock:100"))
                .when(gameWaitingTimeoutProcessor)
                .processTimeoutWithLock(100L);

        // when
        service.processTimeouts();

        // then
        verify(gameWaitingTimeoutProcessor).processTimeoutWithLock(100L);
        verify(gameWaitingTimeoutProcessor).processTimeoutWithLock(101L);
    }

    @Test
    @DisplayName("개별 gameRoom 처리 예외는 batch 전체를 중단하지 않는다")
    void processTimeouts_Exception_ContinueNext() {
        // given
        when(clock.millis()).thenReturn(10_000L);
        when(gameWaitingStore.findDueTimeouts(
                10_000L,
                GameWaitingConstants.WAITING_TIMEOUT_CANDIDATE_BATCH_SIZE
        )).thenReturn(List.of(100L, 101L));
        doThrow(new IllegalStateException("timeout failed"))
                .when(gameWaitingTimeoutProcessor)
                .processTimeoutWithLock(100L);

        // when
        service.processTimeouts();

        // then
        verify(gameWaitingTimeoutProcessor).processTimeoutWithLock(100L);
        verify(gameWaitingTimeoutProcessor).processTimeoutWithLock(101L);
    }
}
