package com.sang.smite.matching.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 매칭 수락/거절/timeout 응답 처리 모니터링에 사용하는 메트릭 이름 및 태그 상수입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MatchResponseMetricNames {

    public static final String REQUESTS = "match.response.requests";
    public static final String COMPLETIONS = "match.response.completions";
    public static final String LOCK_FAILURES = "match.response.lock.failures";

    public static final String TIMEOUT_SETTLEMENTS = "match.response.timeout.settlements";
    public static final String TIMEOUT_QUEUE_RETURNED_USERS = "match.response.timeout.queue_returned.users";
    public static final String TIMEOUT_CLAIMS = "match.response.timeout.claims";
    public static final String TIMEOUT_RECLAIMS = "match.response.timeout.reclaims";
    public static final String TIMEOUT_BATCH_DURATION = "match.response.timeout.batch.duration";
    public static final String TIMEOUT_PROCESSING_DELAY = "match.response.timeout.processing.delay";
    public static final String TIMEOUT_PENDING_BACKLOG = "match.response.timeout.pending.backlog";
    public static final String TIMEOUT_PROCESSING_BACKLOG = "match.response.timeout.processing.backlog";
    public static final String TIMEOUT_OVERDUE_PENDING = "match.response.timeout.overdue.pending";

    public static final String TAG_ACTION = "action";
    public static final String TAG_RESULT = "result";
    public static final String TAG_REASON = "reason";
    public static final String TAG_OUTCOME = "outcome";

    public static final String ACTION_ACCEPT = "accept";
    public static final String ACTION_REJECT = "reject";

    public static final String RESULT_ATTEMPT = "attempt";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";
    public static final String REASON_NONE = "none";

    public static final String COMPLETION_ACCEPTED = "accepted";
    public static final String COMPLETION_DECLINED = "declined";

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
    public static final String OUTCOME_NO_OP = "no_op";
    public static final String OUTCOME_CLAIMED = "claimed";
    public static final String OUTCOME_SKIPPED = "skipped";
    public static final String OUTCOME_RECLAIMED = "reclaimed";
}
