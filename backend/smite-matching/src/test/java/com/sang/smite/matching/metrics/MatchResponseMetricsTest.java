package com.sang.smite.matching.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MatchResponseMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MatchResponseMetrics metrics = new MatchResponseMetrics(meterRegistry);

    @Test
    @DisplayName("accept/reject 요청 지표를 action/result 태그로 기록한다")
    void responseRequestCounters() {
        metrics.incrementResponseAttempt(MatchResponseMetricNames.ACTION_ACCEPT);
        metrics.incrementResponseSuccess(MatchResponseMetricNames.ACTION_ACCEPT);
        metrics.incrementResponseFailure(MatchResponseMetricNames.ACTION_REJECT, "MATCH_006");

        assertThat(meterRegistry.get(MatchResponseMetricNames.REQUESTS)
                .tag(MatchResponseMetricNames.TAG_ACTION, MatchResponseMetricNames.ACTION_ACCEPT)
                .tag(MatchResponseMetricNames.TAG_RESULT, MatchResponseMetricNames.RESULT_ATTEMPT)
                .tag(MatchResponseMetricNames.TAG_REASON, MatchResponseMetricNames.REASON_NONE)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.REQUESTS)
                .tag(MatchResponseMetricNames.TAG_ACTION, MatchResponseMetricNames.ACTION_ACCEPT)
                .tag(MatchResponseMetricNames.TAG_RESULT, MatchResponseMetricNames.RESULT_SUCCESS)
                .tag(MatchResponseMetricNames.TAG_REASON, MatchResponseMetricNames.REASON_NONE)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.REQUESTS)
                .tag(MatchResponseMetricNames.TAG_ACTION, MatchResponseMetricNames.ACTION_REJECT)
                .tag(MatchResponseMetricNames.TAG_RESULT, MatchResponseMetricNames.RESULT_FAILURE)
                .tag(MatchResponseMetricNames.TAG_REASON, "MATCH_006")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("최종 매칭 응답 완료 지표를 기록한다")
    void completionCounters() {
        metrics.incrementAcceptedCompletion();
        metrics.incrementDeclinedCompletion();

        assertThat(meterRegistry.get(MatchResponseMetricNames.COMPLETIONS)
                .tag(MatchResponseMetricNames.TAG_RESULT, MatchResponseMetricNames.COMPLETION_ACCEPTED)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.COMPLETIONS)
                .tag(MatchResponseMetricNames.TAG_RESULT, MatchResponseMetricNames.COMPLETION_DECLINED)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("timeout claim/reclaim/settlement 지표를 기록한다")
    void timeoutCounters() {
        metrics.incrementTimeoutClaim(MatchResponseMetricNames.OUTCOME_CLAIMED);
        metrics.incrementTimeoutReclaim(MatchResponseMetricNames.OUTCOME_RECLAIMED);
        metrics.incrementTimeoutSettlement(MatchResponseMetricNames.OUTCOME_SUCCESS);
        metrics.incrementTimeoutReturnedUsers(2);

        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_CLAIMS)
                .tag(MatchResponseMetricNames.TAG_OUTCOME, MatchResponseMetricNames.OUTCOME_CLAIMED)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_RECLAIMS)
                .tag(MatchResponseMetricNames.TAG_OUTCOME, MatchResponseMetricNames.OUTCOME_RECLAIMED)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_SETTLEMENTS)
                .tag(MatchResponseMetricNames.TAG_OUTCOME, MatchResponseMetricNames.OUTCOME_SUCCESS)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_QUEUE_RETURNED_USERS)
                .counter()
                .count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("timeout backlog gauge를 등록하고 현재 값을 읽는다")
    void timeoutBacklogGauges() {
        AtomicInteger pending = new AtomicInteger(3);
        AtomicInteger processing = new AtomicInteger(1);
        AtomicInteger overdue = new AtomicInteger(2);

        metrics.registerTimeoutBacklogGauges(pending::get, processing::get, overdue::get);

        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_PENDING_BACKLOG).gauge().value())
                .isEqualTo(3.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_PROCESSING_BACKLOG).gauge().value())
                .isEqualTo(1.0);
        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_OVERDUE_PENDING).gauge().value())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("timeout batch duration과 processing delay timer를 기록한다")
    void timeoutTimers() {
        Timer.Sample sample = metrics.startTimer();
        metrics.recordTimeoutBatchDuration(sample);
        metrics.recordTimeoutProcessingDelay(1_500L);

        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_BATCH_DURATION).timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get(MatchResponseMetricNames.TIMEOUT_PROCESSING_DELAY)
                .timer()
                .max(TimeUnit.MILLISECONDS)).isEqualTo(1_500.0);
    }
}
