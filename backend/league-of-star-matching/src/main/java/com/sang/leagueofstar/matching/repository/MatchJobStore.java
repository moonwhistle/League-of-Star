package com.sang.leagueofstar.matching.repository;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;

import java.time.Duration;
import java.util.List;

/**
 * Redis Stream Consumer Group 기반 MatchJob 저장소입니다.
 */
public interface MatchJobStore {

    void initializeConsumerGroup();

    List<MatchClaim> readNew(String consumerName, int count, Duration blockTimeout);

    MatchJobRecoveryBatch autoClaim(
            String consumerName,
            long minIdleMillis,
            String startCursor,
            int count
    );

    int complete(List<MatchClaim> claims, long statusTtlSeconds);

    long pendingCount();

    long streamSize();
}
