package com.sang.leagueofstar.matching.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 대기열 모니터링에 사용하는 메트릭 이름 및 태그 상수입니다.
 *
 * <p>Magic String 방지 및 Grafana PromQL 작성 시 이름 일관성을 보장합니다.
 * Prometheus 지표 이름은 점(.)을 언더스코어(_)로 자동 변환합니다.
 *
 * <pre>
 * 지표 네이밍 규칙: match_{영역}_{대상}_{측정항목}
 * - Counter는 Micrometer가 Prometheus 노출 시 _total suffix를 붙입니다.
 * - Timer는 Micrometer가 Prometheus 노출 시 _seconds suffix를 붙입니다.
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchQueueMetrics {

    // ─────────────────────────────────────────────────
    // 지표 이름 (Micrometer → Prometheus 변환 시 '.' → '_')
    // ─────────────────────────────────────────────────

    /** 티어별 대기열 현재 인원 수 (Gauge) → Prometheus: match_queue_size */
    public static final String QUEUE_SIZE        = "match.queue.size";

    /** 매칭 엔진 1회 스캔 소요 시간 (Timer) → Prometheus: match_engine_scan_duration_seconds */
    public static final String ENGINE_SCAN_DURATION = "match.engine.scan.duration";

    /** 매칭 엔진 1회 스캔 대상 티켓 수 (DistributionSummary) → Prometheus: match_engine_scan_tickets */
    public static final String ENGINE_SCAN_TICKETS = "match.engine.scan.tickets";

    /** 매칭 엔진 성사 페어 수 (Counter) → Prometheus: match_engine_pairs_total */
    public static final String ENGINE_PAIRS = "match.engine.pairs";

    /** 매칭 엔진 1회 스캔당 성사 페어 수 (DistributionSummary) → Prometheus: match_engine_pairs_per_scan */
    public static final String ENGINE_PAIRS_PER_SCAN = "match.engine.pairs.per.scan";

    /** Lua 원자 제거 시도 횟수 (Counter) → Prometheus: match_engine_atomic_pair_attempts_total */
    public static final String ENGINE_ATOMIC_PAIR_ATTEMPTS = "match.engine.atomic_pair.attempts";

    /** Lua 원자 제거 실패 횟수 (Counter) → Prometheus: match_engine_atomic_pair_failures_total */
    public static final String ENGINE_ATOMIC_PAIR_FAILURES = "match.engine.atomic_pair.failures";

    /** 매칭된 유저의 큐 대기 시간 (Timer) → Prometheus: match_engine_matched_user_wait_duration_seconds */
    public static final String ENGINE_MATCHED_USER_WAIT_DURATION = "match.engine.matched_user.wait.duration";

    /** 매칭 엔진 락 획득 실패로 스킵된 스캔 수 (Counter) → Prometheus: match_engine_lock_skipped_total */
    public static final String ENGINE_LOCK_SKIPPED = "match.engine.lock.skipped";

    // ─────────────────────────────────────────────────
    // 태그 키
    // ─────────────────────────────────────────────────

    /** 유저의 티어 점수 (1~28) */
    public static final String TAG_TIER   = "tier";

}
