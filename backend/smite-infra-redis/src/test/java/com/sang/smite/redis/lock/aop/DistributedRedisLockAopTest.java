package com.sang.smite.redis.lock.aop;

import com.sang.smite.redis.lock.annotation.DistributedRedisLock;
import com.sang.smite.redis.lock.exception.RedisLockAcquisitionException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedRedisLockAopTest {

    private RedissonClient redissonClient;
    private RLock lock;
    private DistributedRedisLockAop aop;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        aop = new DistributedRedisLockAop(redissonClient);
    }

    @Test
    @DisplayName("watchdog lock 획득에 성공하면 원본 메서드를 실행하고 lock을 해제한다")
    void lockWithWatchdog() throws Throwable {
        Method method = TestLockTarget.class.getMethod("watchdogLock", String.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-1"}, "result");
        when(redissonClient.getLock("LOCK:match:session:lock:match-1")).thenReturn(lock);
        when(lock.tryLock(500L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        Object result = aop.lock(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(lock).tryLock(500L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("leaseTime이 양수이면 고정 leaseTime으로 lock을 시도한다")
    void lockWithLeaseTime() throws Throwable {
        Method method = TestLockTarget.class.getMethod("fixedLeaseLock", String.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-2"}, "result");
        when(redissonClient.getLock("LOCK:match:session:lock:match-2")).thenReturn(lock);
        when(lock.tryLock(1000L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        Object result = aop.lock(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(lock).tryLock(1000L, 3000L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("lock 획득에 실패하면 예외를 던지고 원본 메서드를 실행하지 않는다")
    void failToAcquireLock() throws Throwable {
        Method method = TestLockTarget.class.getMethod("watchdogLock", String.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-3"}, "result");
        when(redissonClient.getLock("LOCK:match:session:lock:match-3")).thenReturn(lock);
        when(lock.tryLock(500L, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThatThrownBy(() -> aop.lock(joinPoint))
                .isInstanceOf(RedisLockAcquisitionException.class)
                .hasMessageContaining("LOCK:match:session:lock:match-3");

        verify(lock).isHeldByCurrentThread();
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("원본 메서드의 InterruptedException은 lock 획득 실패 예외로 감싸지 않는다")
    void keepOriginalInterruptedExceptionFromProceed() throws Throwable {
        Method method = TestLockTarget.class.getMethod("watchdogLock", String.class);
        InterruptedException exception = new InterruptedException("business interrupted");
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-4"}, exception);
        when(redissonClient.getLock("LOCK:match:session:lock:match-4")).thenReturn(lock);
        when(lock.tryLock(500L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> aop.lock(joinPoint))
                .isSameAs(exception);

        verify(lock).unlock();
    }

    private ProceedingJoinPoint joinPoint(Method method, Object[] args, Object proceedResult) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        if (proceedResult instanceof Throwable throwable) {
            when(joinPoint.proceed()).thenThrow(throwable);
        } else {
            when(joinPoint.proceed()).thenReturn(proceedResult);
        }
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"matchId"});

        return joinPoint;
    }

    static class TestLockTarget {

        @DistributedRedisLock(key = "'match:session:lock:' + #matchId")
        public String watchdogLock(String matchId) {
            return matchId;
        }

        @DistributedRedisLock(key = "'match:session:lock:' + #matchId", waitTime = 1000L, leaseTime = 3000L)
        public String fixedLeaseLock(String matchId) {
            return matchId;
        }
    }
}
