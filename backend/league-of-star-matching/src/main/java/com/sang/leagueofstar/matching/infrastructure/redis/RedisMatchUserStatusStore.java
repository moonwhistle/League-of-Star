package com.sang.leagueofstar.matching.infrastructure.redis;

import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.repository.MatchUserStatusStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
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
    public boolean setStatusIfAbsent(Long userId, MatchStatus status, long ttlSeconds) {
        String key = getStatusKey(userId);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        return bucket.setIfAbsent(status.name(), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<MatchStatus> getStatus(Long userId) {
        String key = getStatusKey(userId);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        return Optional.ofNullable(bucket.get()).map(MatchStatus::valueOf);
    }

    @Override
    public void updateStatus(Long userId, MatchStatus status, long ttlSeconds) {
        String key = getStatusKey(userId);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        bucket.set(status.name(), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void removeStatus(Long userId) {
        String key = getStatusKey(userId);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        bucket.delete();
    }

    private String getStatusKey(Long userId) {
        return MatchingConstants.STATUS_KEY_PREFIX + userId;
    }
}
