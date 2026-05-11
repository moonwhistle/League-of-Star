package com.sang.smite.matching.infrastructure;

import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.repository.MatchTimeoutStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Redis ZSET 기반 timeout job 저장소입니다.
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchTimeoutStore implements MatchTimeoutStore {

    private final RedissonClient redissonClient;
    private String claimScript;
    private String reclaimScript;

    @PostConstruct
    public void init() {
        this.claimScript = loadLuaScript(MatchingConstants.TIMEOUT_CLAIM_LUA_SCRIPT_PATH);
        this.reclaimScript = loadLuaScript(MatchingConstants.TIMEOUT_RECLAIM_LUA_SCRIPT_PATH);
    }

    @Override
    public void addPending(String matchId, long deadlineMillis) {
        pendingSet().add(deadlineMillis, matchId);
    }

    @Override
    public List<String> findDuePending(long nowMillis, int batchSize) {
        return findDue(pendingSet(), nowMillis, batchSize);
    }

    @Override
    public boolean claim(String matchId, long nowMillis, long processingExpireAtMillis) {
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                claimScript,
                RScript.ReturnType.INTEGER,
                timeoutKeys(),
                matchId,
                nowMillis,
                processingExpireAtMillis
        );

        return result != null && result == 1L;
    }

    @Override
    public List<String> findExpiredProcessing(long nowMillis, int batchSize) {
        return findDue(processingSet(), nowMillis, batchSize);
    }

    @Override
    public boolean reclaim(String matchId, long nowMillis, long nextDeadlineMillis) {
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                reclaimScript,
                RScript.ReturnType.INTEGER,
                timeoutKeys(),
                matchId,
                nowMillis,
                nextDeadlineMillis
        );

        return result != null && result == 1L;
    }

    @Override
    public void ack(String matchId) {
        processingSet().remove(matchId);
    }

    @Override
    public void cleanup(String matchId) {
        pendingSet().remove(matchId);
        processingSet().remove(matchId);
    }

    private List<String> findDue(RScoredSortedSet<String> set, long nowMillis, int batchSize) {
        Collection<String> values = set.valueRange(0, true, nowMillis, true, 0, batchSize);
        return new ArrayList<>(values);
    }

    private RScoredSortedSet<String> pendingSet() {
        return redissonClient.getScoredSortedSet(MatchingConstants.TIMEOUT_PENDING_KEY, StringCodec.INSTANCE);
    }

    private RScoredSortedSet<String> processingSet() {
        return redissonClient.getScoredSortedSet(MatchingConstants.TIMEOUT_PROCESSING_KEY, StringCodec.INSTANCE);
    }

    private List<Object> timeoutKeys() {
        return List.of(MatchingConstants.TIMEOUT_PENDING_KEY, MatchingConstants.TIMEOUT_PROCESSING_KEY);
    }

    private String loadLuaScript(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MatchingException(MatchingErrorCode.MATCH_LUA_SCRIPT_ERROR);
        }
    }
}
