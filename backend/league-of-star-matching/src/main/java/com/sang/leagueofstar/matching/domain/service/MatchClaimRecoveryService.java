package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.metrics.MatchEngineMetrics;
import com.sang.leagueofstar.matching.repository.MatchJobRecoveryBatch;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ACK되지 않은 PEL 메시지를 XAUTOCLAIM으로 가져와 재처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchClaimRecoveryService {

    private static final String INITIAL_CURSOR = "0-0";

    private final MatchJobStore matchJobStore;
    private final MatchJobProcessor matchJobProcessor;
    private final MatchConsumerIdentity consumerIdentity;
    private final MatchEngineMetrics matchEngineMetrics;
    private String cursor = INITIAL_CURSOR;

    public void recoverExpiredClaims() {
        MatchJobRecoveryBatch batch = matchJobStore.autoClaim(
                consumerIdentity.recoveryConsumerName(),
                MatchingConstants.MATCH_JOB_MIN_IDLE_MILLIS,
                cursor,
                MatchingConstants.MATCH_CLAIM_RECOVERY_BATCH_SIZE
        );
        cursor = normalizeCursor(batch.nextCursor());

        if (batch.claims().isEmpty()) {
            return;
        }

        matchEngineMetrics.incrementRecoveredClaims(batch.claims().size());
        log.warn("Recovered pending MatchJobs: count={}", batch.claims().size());
        matchJobProcessor.process(batch.claims());
    }

    private String normalizeCursor(String nextCursor) {
        return nextCursor == null || nextCursor.isBlank() ? INITIAL_CURSOR : nextCursor;
    }
}
