package com.sang.leagueofstar.redis.lock.aop;

import com.sang.leagueofstar.redis.lock.annotation.DistributedLock;
import com.sang.leagueofstar.redis.lock.exception.RedisLockAcquisitionException;
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

class DistributedLockAopTest {

    private RedissonClient redissonClient;
    private RLock lock;
    private AopForTransaction aopForTransaction;
    private DistributedLockAop aop;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        aopForTransaction = mock(AopForTransaction.class);
        aop = new DistributedLockAop(redissonClient, aopForTransaction);
    }

    @Test
    @DisplayName("고정 leaseTime lock 획득에 성공하면 트랜잭션 처리기를 통해 원본 메서드를 실행한다")
    void lockWithFixedLeaseTime() throws Throwable {
        Method method = TestLockTarget.class.getMethod("fixedLeaseLock", String.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-1"});
        when(redissonClient.getLock("LOCK:match:session:lock:match-1")).thenReturn(lock);
        when(lock.tryLock(5000L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(aopForTransaction.proceed(joinPoint)).thenReturn("result");

        Object result = aop.lock(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(lock).tryLock(5000L, 3000L, TimeUnit.MILLISECONDS);
        verify(aopForTransaction).proceed(joinPoint);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("leaseTime이 음수이면 watchdog lock을 사용한다")
    void lockWithWatchdog() throws Throwable {
        Method method = TestLockTarget.class.getMethod("watchdogLock", String.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-2"});
        when(redissonClient.getLock("LOCK:match:session:lock:match-2")).thenReturn(lock);
        when(lock.tryLock(5000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(aopForTransaction.proceed(joinPoint)).thenReturn("result");

        Object result = aop.lock(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(lock).tryLock(5000L, TimeUnit.MILLISECONDS);
        verify(aopForTransaction).proceed(joinPoint);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("lock 획득에 실패하면 예외를 던지고 원본 메서드를 실행하지 않는다")
    void failToAcquireLock() throws Throwable {
        Method method = TestLockTarget.class.getMethod("fixedLeaseLock", String.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-3"});
        when(redissonClient.getLock("LOCK:match:session:lock:match-3")).thenReturn(lock);
        when(lock.tryLock(5000L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThatThrownBy(() -> aop.lock(joinPoint))
                .isInstanceOf(RedisLockAcquisitionException.class)
                .hasMessageContaining("LOCK:match:session:lock:match-3");

        verify(aopForTransaction, never()).proceed(joinPoint);
        verify(lock).isHeldByCurrentThread();
    }

    @Test
    @DisplayName("원본 메서드의 InterruptedException은 lock 획득 실패 예외로 감싸지 않는다")
    void keepOriginalInterruptedExceptionFromProceed() throws Throwable {
        Method method = TestLockTarget.class.getMethod("fixedLeaseLock", String.class);
        InterruptedException exception = new InterruptedException("business interrupted");
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"match-4"});
        when(redissonClient.getLock("LOCK:match:session:lock:match-4")).thenReturn(lock);
        when(lock.tryLock(5000L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(aopForTransaction.proceed(joinPoint)).thenThrow(exception);

        assertThatThrownBy(() -> aop.lock(joinPoint))
                .isSameAs(exception);

        verify(lock).unlock();
    }

    private ProceedingJoinPoint joinPoint(Method method, Object[] args) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"matchId"});

        return joinPoint;
    }

    static class TestLockTarget {

        @DistributedLock(key = "'match:session:lock:' + #matchId")
        public String fixedLeaseLock(String matchId) {
            return matchId;
        }

        @DistributedLock(key = "'match:session:lock:' + #matchId", leaseTime = -1L)
        public String watchdogLock(String matchId) {
            return matchId;
        }
    }
}
