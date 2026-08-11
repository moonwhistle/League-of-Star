package com.sang.leagueofstar.matching.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 대기열 모니터링에 사용하는 메트릭 이름입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchQueueMetrics {

    public static final String QUEUE_SIZE = "match.queue.size";
    public static final String PROCESSING_SIZE = "match.processing.size";
    public static final String ENGINE_SCAN_DURATION = "match.engine.scan.duration";
    public static final String ENGINE_SCAN_TICKETS = "match.engine.scan.tickets";
    public static final String ENGINE_PAIRS = "match.engine.pairs";
    public static final String ENGINE_PAIRS_PER_SCAN = "match.engine.pairs.per.scan";
    public static final String ENGINE_ATOMIC_BATCH_ATTEMPTS = "match.engine.atomic_batch.attempts";
    public static final String ENGINE_ATOMIC_BATCH_FAILURES = "match.engine.atomic_batch.failures";
    public static final String ENGINE_MATCHED_USER_WAIT_DURATION = "match.engine.matched_user.wait.duration";
    public static final String ENGINE_RECOVERED_CLAIMS = "match.engine.recovered_claims";
}
