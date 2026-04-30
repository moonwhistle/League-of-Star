package com.sang.smite.matching.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MatchEngineMetricsTest {

    private MeterRegistry meterRegistry;
    private MatchEngineMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new MatchEngineMetrics(meterRegistry);
    }

    @Test
    @DisplayName("scan.duration Timer가 의도한 이름으로 측정된다")
    void recordScanDuration() {
        Timer.Sample sample = metrics.startScanTimer();
        metrics.recordScanDuration(sample);

        Timer timer = meterRegistry.find(MatchQueueMetrics.ENGINE_SCAN_DURATION).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("scan.tickets DistributionSummary가 의도한 이름으로 기록된다")
    void recordScannedTickets() {
        metrics.recordScannedTickets(5);

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_SCAN_TICKETS).summary()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_SCAN_TICKETS).summary()).count()).isEqualTo(1);
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_SCAN_TICKETS).summary()).max()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("pairs.per.scan DistributionSummary가 의도한 이름으로 기록된다")
    void recordPairsPerScan() {
        metrics.recordPairsPerScan(3);

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_PAIRS_PER_SCAN).summary()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_PAIRS_PER_SCAN).summary()).count()).isEqualTo(1);
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_PAIRS_PER_SCAN).summary()).max()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("pairs Counter가 의도한 이름으로 증가한다")
    void incrementPairs() {
        metrics.incrementPairs();

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_PAIRS).counter()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_PAIRS).counter()).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("atomic_pair.attempts Counter가 의도한 이름으로 증가한다")
    void incrementAtomicPairAttempts() {
        metrics.incrementAtomicPairAttempts();

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_PAIR_ATTEMPTS).counter()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_PAIR_ATTEMPTS).counter()).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("atomic_pair.failures Counter가 의도한 이름으로 증가한다")
    void incrementAtomicPairFailures() {
        metrics.incrementAtomicPairFailures();

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_PAIR_FAILURES).counter()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_PAIR_FAILURES).counter()).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("matched_user.wait.duration Timer가 의도한 이름으로 측정된다")
    void recordMatchedUserWait() {
        metrics.recordMatchedUserWait(5000L);

        Timer timer = meterRegistry.find(MatchQueueMetrics.ENGINE_MATCHED_USER_WAIT_DURATION).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("lock.skipped Counter가 의도한 이름으로 증가한다")
    void incrementLockSkipped() {
        metrics.incrementLockSkipped();

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_LOCK_SKIPPED).counter()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_LOCK_SKIPPED).counter()).count()).isEqualTo(1.0);
    }
}
