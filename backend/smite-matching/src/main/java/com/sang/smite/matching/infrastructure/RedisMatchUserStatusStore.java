package com.sang.smite.matching.infrastructure;

import com.sang.smite.domain.match.domain.vo.MatchStatus;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis를 사용하여 유저의 매칭 상태를 관리하는 구현체입니다.
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchUserStatusStore implements MatchUserStatusStore {

    private final RedissonClient redissonClient;

    @Override
    public void setStatus(Long userId, MatchStatus status, long ttlSeconds) {
        String key = getStatusKey(userId);
        RBucket<MatchStatus> bucket = redissonClient.getBucket(key);
        bucket.set(status, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<MatchStatus> getStatus(Long userId) {
        String key = getStatusKey(userId);
        RBucket<MatchStatus> bucket = redissonClient.getBucket(key);
        return Optional.ofNullable(bucket.get());
    }

    @Override
    public void removeStatus(Long userId) {
        String key = getStatusKey(userId);
        RBucket<MatchStatus> bucket = redissonClient.getBucket(key);
        bucket.delete();
    }

    private String getStatusKey(Long userId) {
        return MatchingConstants.STATUS_KEY_PREFIX + userId;
    }
}
