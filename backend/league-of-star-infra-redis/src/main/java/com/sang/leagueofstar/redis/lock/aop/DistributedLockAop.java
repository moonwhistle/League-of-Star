package com.sang.leagueofstar.redis.lock.aop;

import com.sang.leagueofstar.redis.common.constant.LockConstants;
import com.sang.leagueofstar.redis.lock.annotation.DistributedLock;
import com.sang.leagueofstar.redis.lock.exception.RedisLockAcquisitionException;
import com.sang.leagueofstar.redis.lock.parser.CustomSpringELParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 분산 락의 핵심 흐름을 제어하는 Aspect 클래스
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class DistributedLockAop {

    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(com.sang.leagueofstar.redis.lock.annotation.DistributedLock)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);
        String key = createLockKey(signature, joinPoint.getArgs(), distributedLock.key());
        RLock rLock = redissonClient.getLock(key);

        try {
            boolean available = tryLock(rLock, distributedLock, key);
            if (!available) {
                log.warn("Lock acquisition failed for key: {}", key);
                throw new RedisLockAcquisitionException(key);
            }

            /* [REQUIRES_NEW 트랜잭션 설계 의도]
             * AopForTransaction.proceed()는 REQUIRES_NEW 트랜잭션 안에서 비즈니스 로직을 실행
             * 트랜잭션이 완전히 커밋된 후 finally 블록에서 락이 해제되므로,
             * 락 해제와 트랜잭션 롤백 사이의 타이밍 문제가 발생하지 않음
             */
            return aopForTransaction.proceed(joinPoint);
        } finally {
            unlockIfHeldByCurrentThread(rLock, method, key);
        }
    }

    private String createLockKey(MethodSignature signature, Object[] args, String lockKeyExpression) {
        Object dynamicValue = CustomSpringELParser.getDynamicValue(
                signature.getParameterNames(),
                args,
                lockKeyExpression
        );
        return LockConstants.REDISSON_LOCK_PREFIX + dynamicValue;
    }

    private boolean tryLock(RLock lock, DistributedLock distributedLock, String key) {
        try {
            if (distributedLock.leaseTime() < 0) {
                return lock.tryLock(distributedLock.waitTime(), distributedLock.timeUnit());
            }

            return lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
        } catch (InterruptedException e) {
            log.error("Lock acquisition interrupted for key: {}", key, e);
            Thread.currentThread().interrupt();
            throw new RedisLockAcquisitionException(key, e);
        }
    }

    private void unlockIfHeldByCurrentThread(RLock lock, Method method, String key) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (IllegalMonitorStateException e) {
            log.info("Redis lock already unlocked: method={}, key={}", method.getName(), key);
        }
    }
}
