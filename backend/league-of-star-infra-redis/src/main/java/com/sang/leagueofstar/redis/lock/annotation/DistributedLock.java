package com.sang.leagueofstar.redis.lock.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 분산 락을 선언적으로 적용하기 위한 어노테이션입니다.
 *
 * <p>{@code leaseTime}을 음수로 설정하면 Redisson watchdog을 사용합니다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
    long waitTime() default 5000L;
    long leaseTime() default 3000L;
}
