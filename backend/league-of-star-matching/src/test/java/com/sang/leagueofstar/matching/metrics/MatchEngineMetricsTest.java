package com.sang.leagueofstar.matching.metrics;

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
    @DisplayName("atomic_batch.attempts Counter가 의도한 이름으로 증가한다")
    void incrementAtomicBatchAttempts() {
        metrics.incrementAtomicBatchAttempts();

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_BATCH_ATTEMPTS).counter()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_BATCH_ATTEMPTS).counter()).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("atomic_batch.failures Counter가 의도한 이름으로 증가한다")
    void incrementAtomicBatchFailures() {
        metrics.incrementAtomicBatchFailures();

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_BATCH_FAILURES).counter()).isNotNull();
        assertThat(Objects.requireNonNull(meterRegistry.find(MatchQueueMetrics.ENGINE_ATOMIC_BATCH_FAILURES).counter()).count()).isEqualTo(1.0);
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
    @DisplayName("recovered_claims Counter에 복구한 claim 수를 기록한다")
    void incrementRecoveredClaims() {
        metrics.incrementRecoveredClaims(3);

        assertThat(meterRegistry.find(MatchQueueMetrics.ENGINE_RECOVERED_CLAIMS).counter()).isNotNull();
        assertThat(Objects.requireNonNull(
                meterRegistry.find(MatchQueueMetrics.ENGINE_RECOVERED_CLAIMS).counter()
        ).count()).isEqualTo(3.0);
    }

}
