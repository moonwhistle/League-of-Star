package com.sang.leagueofstar.redis.lock.exception;

/**
 * Redis 분산 락 획득에 실패했을 때 발생하는 예외입니다.
 */
public class RedisLockAcquisitionException extends RuntimeException {

    public RedisLockAcquisitionException(String key) {
        super("Redis lock acquisition failed: " + key);
    }

    public RedisLockAcquisitionException(String key, Throwable cause) {
        super("Redis lock acquisition interrupted: " + key, cause);
    }
}
