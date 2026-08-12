package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.matching.metrics.MatchEngineMetrics;
import com.sang.leagueofstar.matching.repository.MatchQueueStore;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.sang.leagueofstar.matching.common.constant.MatchingConstants.MATCH_CLAIM_BATCH_SIZE;
import static com.sang.leagueofstar.matching.common.constant.MatchingConstants.MATCH_MAX_BATCHES_PER_SCAN;

/**
 * FIFO 티켓을 페어링해 Redis Stream MatchJob으로 전환하는 Producer입니다.
 */
@Service
@RequiredArgsConstructor
public class MatchPairingService {

    private final MatchQueueStore matchStore;
    private final MatchEngineMetrics matchEngineMetrics;

    /**
     * Lua가 빈 결과를 반환하거나 스캔당 최대 배치 수에 도달할 때까지 MatchJob을 생성합니다.
     */
    public void processMatching() {
        Timer.Sample sample = matchEngineMetrics.startScanTimer();
        int producedPairs = 0;

        try {
            matchEngineMetrics.recordScannedTickets(matchStore.count());
            for (int batchIndex = 0; batchIndex < MATCH_MAX_BATCHES_PER_SCAN; batchIndex++) {
                int produced = enqueueOldestMatches();
                producedPairs += produced;
                if (produced < MATCH_CLAIM_BATCH_SIZE / 2) {
                    break;
                }
            }
        } finally {
            matchEngineMetrics.recordPairsPerScan(producedPairs);
            matchEngineMetrics.recordScanDuration(sample);
        }
    }

    private int enqueueOldestMatches() {
        matchEngineMetrics.incrementAtomicBatchAttempts();
        try {
            return matchStore.enqueueOldestMatches(MATCH_CLAIM_BATCH_SIZE);
        } catch (RuntimeException e) {
            matchEngineMetrics.incrementAtomicBatchFailures();
            throw e;
        }
    }
}
