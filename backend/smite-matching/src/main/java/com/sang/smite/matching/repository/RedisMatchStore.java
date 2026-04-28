package com.sang.smite.matching.repository;

import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.domain.match.domain.vo.MatchTicket;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Redis 기반의 매칭 대기열 저장소 구현체입니다.
 * 티어별 분할 ZSET 구조를 사용하여 성능과 확장성을 보장합니다.
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchStore implements MatchStore {

    private final RedissonClient redissonClient;
    private String atomicRemoveScript;

    @PostConstruct
    public void init() {
        this.atomicRemoveScript = loadLuaScript();
    }

    @Override
    public void add(MatchTicket ticket) {
        String key = getQueueKey(ticket.tierScore());
        RScoredSortedSet<Long> queue = redissonClient.getScoredSortedSet(key);
        queue.add(ticket.entryTime(), ticket.userId());
    }

    @Override
    public void remove(Long userId, int tierScore) {
        String key = getQueueKey(tierScore);
        RScoredSortedSet<Long> queue = redissonClient.getScoredSortedSet(key);
        queue.remove(userId);
    }

    @Override
    public List<MatchTicket> findAll() {
        RBatch batch = redissonClient.createBatch();
        List<RFuture<Collection<ScoredEntry<Long>>>> futures = new ArrayList<>();

        // 파이프라이닝으로 모든 티어 큐를 한 번에 조회
        for (int i = MatchingConstants.TIER_SCORE_MIN; i <= MatchingConstants.TIER_SCORE_MAX; i++) {
            String key = getQueueKey(i);
            RScoredSortedSetAsync<Long> queue = batch.getScoredSortedSet(key);
            futures.add(queue.entryRangeAsync(0, -1));
        }

        batch.execute();

        List<MatchTicket> allTickets = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            int tierScore = MatchingConstants.TIER_SCORE_MIN + i;
            try {
                Collection<ScoredEntry<Long>> entries = futures.get(i).get();
                for (ScoredEntry<Long> entry : entries) {
                    allTickets.add(new MatchTicket(
                            entry.getValue(), 
                            tierScore, 
                            entry.getScore().longValue()
                    ));
                }
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                throw new MatchingException(MatchingErrorCode.MATCH_REDIS_FETCH_ERROR);
            }
        }
        return allTickets;
    }

    @Override
    public boolean atomicPairRemove(Long userAId, int tierAScore, Long userBId, int tierBScore) {
        List<Object> keys = List.of(getQueueKey(tierAScore), getQueueKey(tierBScore));
        
        Long result = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                atomicRemoveScript,
                RScript.ReturnType.INTEGER,
                keys,
                userAId, userBId
        );
        
        return result != null && result == 1L;
    }

    private String getQueueKey(int tierScore) {
        return MatchingConstants.QUEUE_KEY_PREFIX + tierScore;
    }

    private String loadLuaScript() {
        try {
            ClassPathResource resource = new ClassPathResource(MatchingConstants.LUA_SCRIPT_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MatchingException(MatchingErrorCode.MATCH_LUA_SCRIPT_ERROR);
        }
    }
}
