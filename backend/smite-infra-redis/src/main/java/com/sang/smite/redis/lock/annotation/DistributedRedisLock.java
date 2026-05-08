package com.sang.smite.redis.lock.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redis 작업만 수행하는 구간에 Redisson 분산 락을 적용하기 위한 어노테이션입니다.
 *
 * <p>{@code leaseTime}을 음수로 설정하면 Redisson watchdog을 사용합니다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedRedisLock {

    String key();

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    long waitTime() default 500L;

    long leaseTime() default -1L;
}
