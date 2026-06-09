package com.sang.leagueofstar.matching.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 매칭 응답 처리와 timeout scheduler 지표 기록을 캡슐화합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchResponseMetrics {

    private final MeterRegistry meterRegistry;
    private Supplier<Number> pendingBacklogSupplier;
    private Supplier<Number> processingBacklogSupplier;
    private Supplier<Number> overduePendingSupplier;

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void registerTimeoutBacklogGauges(
            Supplier<Number> pendingBacklog,
            Supplier<Number> processingBacklog,
            Supplier<Number> overduePending
    ) {
        this.pendingBacklogSupplier = pendingBacklog;
        this.processingBacklogSupplier = processingBacklog;
        this.overduePendingSupplier = overduePending;
        meterRegistry.gauge(MatchResponseMetricNames.TIMEOUT_PENDING_BACKLOG,
                pendingBacklogSupplier, supplier -> supplier.get().doubleValue());
        meterRegistry.gauge(MatchResponseMetricNames.TIMEOUT_PROCESSING_BACKLOG,
                processingBacklogSupplier, supplier -> supplier.get().doubleValue());
        meterRegistry.gauge(MatchResponseMetricNames.TIMEOUT_OVERDUE_PENDING,
                overduePendingSupplier, supplier -> supplier.get().doubleValue());
    }

    public void incrementResponseAttempt(String action) {
        incrementResponse(action, MatchResponseMetricNames.RESULT_ATTEMPT);
    }

    public void incrementResponseSuccess(String action) {
        incrementResponse(action, MatchResponseMetricNames.RESULT_SUCCESS);
    }

    public void incrementResponseFailure(String action, String reason) {
        meterRegistry.counter(
                MatchResponseMetricNames.REQUESTS,
                MatchResponseMetricNames.TAG_ACTION,
                action,
                MatchResponseMetricNames.TAG_RESULT,
                MatchResponseMetricNames.RESULT_FAILURE,
                MatchResponseMetricNames.TAG_REASON,
                reason
        ).increment();
    }

    public void incrementLockFailure(String action) {
        meterRegistry.counter(
                MatchResponseMetricNames.LOCK_FAILURES,
                MatchResponseMetricNames.TAG_ACTION,
                action
        ).increment();
    }

    public void incrementAcceptedCompletion() {
        incrementCompletion(MatchResponseMetricNames.COMPLETION_ACCEPTED);
    }

    public void incrementDeclinedCompletion() {
        incrementCompletion(MatchResponseMetricNames.COMPLETION_DECLINED);
    }

    public void incrementGameSetupFailedCompletion() {
        incrementCompletion(MatchResponseMetricNames.COMPLETION_GAME_SETUP_FAILED);
    }

    public void incrementTimeoutSettlement(String outcome) {
        meterRegistry.counter(
                MatchResponseMetricNames.TIMEOUT_SETTLEMENTS,
                MatchResponseMetricNames.TAG_OUTCOME,
                outcome
        ).increment();
    }

    public void incrementTimeoutReturnedUsers(int count) {
        if (count <= 0) {
            return;
        }
        meterRegistry.counter(MatchResponseMetricNames.TIMEOUT_QUEUE_RETURNED_USERS).increment(count);
    }

    public void incrementTimeoutClaim(String outcome) {
        meterRegistry.counter(
                MatchResponseMetricNames.TIMEOUT_CLAIMS,
                MatchResponseMetricNames.TAG_OUTCOME,
                outcome
        ).increment();
    }

    public void incrementTimeoutReclaim(String outcome) {
        meterRegistry.counter(
                MatchResponseMetricNames.TIMEOUT_RECLAIMS,
                MatchResponseMetricNames.TAG_OUTCOME,
                outcome
        ).increment();
    }

    public void recordTimeoutBatchDuration(Timer.Sample sample) {
        sample.stop(Timer.builder(MatchResponseMetricNames.TIMEOUT_BATCH_DURATION)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    public void recordTimeoutProcessingDelay(long delayMillis) {
        Timer.builder(MatchResponseMetricNames.TIMEOUT_PROCESSING_DELAY)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(delayMillis, 0L)));
    }

    private void incrementResponse(String action, String result) {
        meterRegistry.counter(
                MatchResponseMetricNames.REQUESTS,
                MatchResponseMetricNames.TAG_ACTION,
                action,
                MatchResponseMetricNames.TAG_RESULT,
                result,
                MatchResponseMetricNames.TAG_REASON,
                MatchResponseMetricNames.REASON_NONE
        ).increment();
    }

    private void incrementCompletion(String result) {
        meterRegistry.counter(
                MatchResponseMetricNames.COMPLETIONS,
                MatchResponseMetricNames.TAG_RESULT,
                result
        ).increment();
    }
}
