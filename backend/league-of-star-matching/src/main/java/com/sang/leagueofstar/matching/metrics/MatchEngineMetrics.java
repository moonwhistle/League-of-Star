package com.sang.leagueofstar.matching.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 매칭 엔진 성능 비교에 필요한 Micrometer 계측을 캡슐화합니다.
 *
 * <p>매칭 로직은 어떤 지표를 기록할지만 호출하고, 지표 이름/히스토그램 설정 등
 * Micrometer 세부 구현은 이 클래스에서 관리합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class MatchEngineMetrics {

    private final MeterRegistry meterRegistry;

    public Timer.Sample startScanTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordScanDuration(Timer.Sample sample) {
        sample.stop(Timer.builder(MatchQueueMetrics.ENGINE_SCAN_DURATION)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    public void recordScannedTickets(int ticketCount) {
        DistributionSummary.builder(MatchQueueMetrics.ENGINE_SCAN_TICKETS)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(ticketCount);
    }

    public void recordPairsPerScan(int pairedCount) {
        DistributionSummary.builder(MatchQueueMetrics.ENGINE_PAIRS_PER_SCAN)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(pairedCount);
    }

    public void incrementPairs() {
        meterRegistry.counter(MatchQueueMetrics.ENGINE_PAIRS).increment();
    }

    public void incrementAtomicBatchAttempts() {
        meterRegistry.counter(MatchQueueMetrics.ENGINE_ATOMIC_BATCH_ATTEMPTS).increment();
    }

    public void incrementAtomicBatchFailures() {
        meterRegistry.counter(MatchQueueMetrics.ENGINE_ATOMIC_BATCH_FAILURES).increment();
    }

    public void recordMatchedUserWait(long waitMillis) {
        Timer.builder(MatchQueueMetrics.ENGINE_MATCHED_USER_WAIT_DURATION)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofMillis(waitMillis));
    }

    public void incrementRecoveredClaims(int count) {
        meterRegistry.counter(MatchQueueMetrics.ENGINE_RECOVERED_CLAIMS).increment(count);
    }
}
