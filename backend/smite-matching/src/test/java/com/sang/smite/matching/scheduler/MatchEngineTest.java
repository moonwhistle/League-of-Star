package com.sang.smite.matching.scheduler;

import com.sang.smite.matching.metrics.MatchEngineMetrics;
import com.sang.smite.matching.service.MatchEngineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchEngineTest {

    @InjectMocks
    private MatchEngine matchEngine;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private MatchEngineService matchEngineService;

    @Mock
    private MatchEngineMetrics matchEngineMetrics;

    @Mock
    private RLock rLock;

    @Test
    @DisplayName("락 획득 성공 시 매칭 서비스를 호출하고 unlock을 수행함")
    void processMatching_LockAcquired() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:match:engine")).willReturn(rLock);
        given(rLock.tryLock(0L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        // when
        matchEngine.processMatching();

        // then
        verify(matchEngineService).processMatching();
        verify(matchEngineMetrics, never()).incrementLockSkipped();
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시 매칭 서비스를 호출하지 않고 스킵 메트릭을 증가시킴")
    void processMatching_LockSkipped() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:match:engine")).willReturn(rLock);
        given(rLock.tryLock(0L, TimeUnit.SECONDS)).willReturn(false);
        given(rLock.isHeldByCurrentThread()).willReturn(false);

        // when
        matchEngine.processMatching();

        // then
        verify(matchEngineMetrics).incrementLockSkipped();
        verify(matchEngineService, never()).processMatching();
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("tryLock 중 InterruptedException 발생 시 스레드 인터럽트 상태를 복원하고 unlock하지 않음")
    void processMatching_Interrupted() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:match:engine")).willReturn(rLock);
        given(rLock.tryLock(0L, TimeUnit.SECONDS)).willThrow(new InterruptedException("Test interrupted"));
        given(rLock.isHeldByCurrentThread()).willReturn(false);

        try {
            // when
            matchEngine.processMatching();

            // then
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(matchEngineService, never()).processMatching();
            verify(matchEngineMetrics, never()).incrementLockSkipped();
            verify(rLock, never()).unlock();
        } finally {
            // 상태를 복구하여 다른 테스트에 영향을 주지 않도록 함
            Thread.interrupted();
        }
    }
}
