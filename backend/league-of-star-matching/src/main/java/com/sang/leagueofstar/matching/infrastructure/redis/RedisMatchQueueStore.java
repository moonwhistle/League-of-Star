package com.sang.leagueofstar.matching.infrastructure.redis;

import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.common.exception.MatchingErrorCode;
import com.sang.leagueofstar.matching.common.exception.MatchingException;
import com.sang.leagueofstar.matching.repository.MatchQueueStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 대기 시작 시각을 score로 사용하는 Redis ZSET FIFO 대기열입니다.
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchQueueStore implements MatchQueueStore {

    private final RedissonClient redissonClient;
    private String enqueueMatchJobsScript;

    @PostConstruct
    public void init() {
        enqueueMatchJobsScript = loadLuaScript(MatchingConstants.ENQUEUE_MATCH_JOBS_LUA_SCRIPT_PATH);
    }

    @Override
    public void add(MatchTicket ticket) {
        queue().add(ticket.entryTime(), String.valueOf(ticket.userId()));
    }

    @Override
    public boolean remove(Long userId) {
        return queue().remove(String.valueOf(userId));
    }

    @Override
    public List<MatchTicket> findAll() {
        Collection<ScoredEntry<String>> entries = queue().entryRange(0, -1);
        List<MatchTicket> tickets = new ArrayList<>(entries.size());
        for (ScoredEntry<String> entry : entries) {
            tickets.add(new MatchTicket(Long.valueOf(entry.getValue()), entry.getScore().longValue()));
        }
        return tickets;
    }

    @Override
    public int count() {
        return queue().size();
    }

    @Override
    public int enqueueOldestMatches(int maxUsers) {
        validateBatchSize(maxUsers);
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                enqueueMatchJobsScript,
                RScript.ReturnType.INTEGER,
                List.of(MatchingConstants.QUEUE_KEY, MatchingConstants.MATCH_JOB_STREAM_KEY),
                maxUsers
        );
        return result == null ? 0 : result.intValue();
    }

    private void validateBatchSize(int maxUsers) {
        if (maxUsers < 2 || maxUsers % 2 != 0) {
            throw new IllegalArgumentException("maxUsers must be an even number greater than or equal to 2");
        }
    }

    private RScoredSortedSet<String> queue() {
        return redissonClient.getScoredSortedSet(MatchingConstants.QUEUE_KEY, StringCodec.INSTANCE);
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
