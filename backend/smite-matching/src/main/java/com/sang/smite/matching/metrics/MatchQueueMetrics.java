package com.sang.smite.matching.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 대기열 모니터링에 사용하는 메트릭 이름 및 태그 상수입니다.
 *
 * <p>Magic String 방지 및 Grafana PromQL 작성 시 이름 일관성을 보장합니다.
 * Prometheus 지표 이름은 점(.)을 언더스코어(_)로 자동 변환합니다.
 *
 * <pre>
 * 지표 네이밍 규칙: match_queue_{대상}_{측정항목}_{단위접미사}
 * - 대상: join / leave / size
 * - 측정항목: (없음) / inflight
 * - 단위접미사: total (Counter), seconds (Timer 자동), (없음 - Gauge)
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchQueueMetrics {

    // ─────────────────────────────────────────────────
    // 지표 이름 (Micrometer → Prometheus 변환 시 '.' → '_')
    // ─────────────────────────────────────────────────

    /** 대기열 진입 시도 횟수 (Counter) → Prometheus: match_queue_join_total */
    public static final String JOIN_TOTAL        = "match.queue.join";

    /** 대기열 진입 처리 시간 (Timer) → Prometheus: match_queue_join_duration_seconds */
    public static final String JOIN_DURATION     = "match.queue.join.duration";

    /** 대기열 취소 시도 횟수 (Counter) → Prometheus: match_queue_leave_total */
    public static final String LEAVE_TOTAL       = "match.queue.leave";

    /** 대기열 취소 처리 시간 (Timer) → Prometheus: match_queue_leave_duration_seconds */
    public static final String LEAVE_DURATION    = "match.queue.leave.duration";

    /** 현재 처리 중인 joinQueue 요청 수 (Gauge) → Prometheus: match_queue_join_inflight */
    public static final String JOIN_INFLIGHT     = "match.queue.join.inflight";

    /** 현재 처리 중인 leaveQueue 요청 수 (Gauge) → Prometheus: match_queue_leave_inflight */
    public static final String LEAVE_INFLIGHT    = "match.queue.leave.inflight";

    /** 티어별 대기열 현재 인원 수 (Gauge) → Prometheus: match_queue_size */
    public static final String QUEUE_SIZE        = "match.queue.size";

    // ─────────────────────────────────────────────────
    // 태그 키
    // ─────────────────────────────────────────────────

    /** 유저의 티어 점수 (1~28) */
    public static final String TAG_TIER   = "tier";

    /** 처리 결과 */
    public static final String TAG_RESULT = "result";

    // ─────────────────────────────────────────────────
    // result 태그 값
    // ─────────────────────────────────────────────────

    /** 정상 처리 완료 */
    public static final String RESULT_SUCCESS     = "success";

    /** joinQueue: 이미 대기 중이거나 게임 중 (SETNX 실패) */
    public static final String RESULT_DUPLICATE   = "duplicate";

    /** leaveQueue: 대기 중이 아닌 상태에서 취소 시도 / 엔진이 이미 선점 */
    public static final String RESULT_NOT_IN_QUEUE = "not_in_queue";

    /** 예상치 못한 Redis 오류 */
    public static final String RESULT_ERROR       = "error";
}
