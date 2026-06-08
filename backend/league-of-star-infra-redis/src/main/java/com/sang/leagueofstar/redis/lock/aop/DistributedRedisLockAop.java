package com.sang.leagueofstar.redis.lock.aop;

import com.sang.leagueofstar.redis.common.constant.LockConstants;
import com.sang.leagueofstar.redis.lock.annotation.DistributedRedisLock;
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
 * Redis-only 작업에 분산 락만 적용하는 Aspect입니다.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class DistributedRedisLockAop {

    private final RedissonClient redissonClient;

    @Around("@annotation(com.sang.leagueofstar.redis.lock.annotation.DistributedRedisLock)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedRedisLock distributedRedisLock = method.getAnnotation(DistributedRedisLock.class);
        String key = createLockKey(signature, joinPoint.getArgs(), distributedRedisLock.key());
        RLock lock = redissonClient.getLock(key);

        try {
            boolean locked = tryLock(lock, distributedRedisLock, key);
            if (!locked) {
                log.warn("Redis lock acquisition failed: method={}, key={}", method.getName(), key);
                throw new RedisLockAcquisitionException(key);
            }

            return joinPoint.proceed();
        } finally {
            unlockIfHeldByCurrentThread(lock, method, key);
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

    private boolean tryLock(RLock lock, DistributedRedisLock distributedRedisLock, String key) {
        try {
            if (distributedRedisLock.leaseTime() < 0) {
                return lock.tryLock(distributedRedisLock.waitTime(), distributedRedisLock.timeUnit());
            }

            return lock.tryLock(
                    distributedRedisLock.waitTime(),
                    distributedRedisLock.leaseTime(),
                    distributedRedisLock.timeUnit()
            );
        } catch (InterruptedException e) {
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
