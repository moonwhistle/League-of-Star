package com.sang.smite.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SseNotificationMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SseNotificationMetrics metrics = new SseNotificationMetrics(meterRegistry);

    @Test
    @DisplayName("활성 SSE 연결 gauge를 등록하고 현재 값을 읽는다.")
    void registerActiveConnectionGauge() {
        // given
        AtomicInteger activeConnections = new AtomicInteger(3);
        metrics.registerActiveConnectionGauge(activeConnections::get);

        // when
        Double current = meterRegistry.get(SseNotificationMetricNames.CONNECTIONS_ACTIVE).gauge().value();
        activeConnections.set(7);
        Double updated = meterRegistry.get(SseNotificationMetricNames.CONNECTIONS_ACTIVE).gauge().value();

        // then
        assertThat(current).isEqualTo(3.0);
        assertThat(updated).isEqualTo(7.0);
    }

    @Test
    @DisplayName("SSE 이벤트 전송 시도/성공/실패 카운터를 증가시킨다.")
    void incrementSendCounters() {
        // when
        metrics.incrementSendAttempt("heartbeat");
        metrics.recordSendSuccess("heartbeat", metrics.startSendTimer());
        metrics.recordSendFailure("match_found", metrics.startSendTimer());

        // then
        Counter attempts = meterRegistry.get(SseNotificationMetricNames.EVENTS_SEND_ATTEMPTS)
                .tag(SseNotificationMetricNames.TAG_EVENT, "heartbeat")
                .counter();
        Counter success = meterRegistry.get(SseNotificationMetricNames.EVENTS_SEND_SUCCESS)
                .tag(SseNotificationMetricNames.TAG_EVENT, "heartbeat")
                .counter();
        Counter failures = meterRegistry.get(SseNotificationMetricNames.EVENTS_SEND_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, "match_found")
                .counter();

        assertThat(attempts.count()).isEqualTo(1.0);
        assertThat(success.count()).isEqualTo(1.0);
        assertThat(failures.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("이벤트 전송 지연 Timer를 결과 태그와 함께 기록한다.")
    void recordSendDuration() {
        // when
        metrics.recordSendSuccess("connected", metrics.startSendTimer());
        metrics.recordSendFailure("connected", metrics.startSendTimer());

        // then
        Timer successTimer = meterRegistry.get(SseNotificationMetricNames.EVENT_SEND_DURATION)
                .tag(SseNotificationMetricNames.TAG_EVENT, "connected")
                .tag("result", "success")
                .timer();
        Timer failureTimer = meterRegistry.get(SseNotificationMetricNames.EVENT_SEND_DURATION)
                .tag(SseNotificationMetricNames.TAG_EVENT, "connected")
                .tag("result", "failure")
                .timer();

        assertThat(successTimer.count()).isEqualTo(1);
        assertThat(failureTimer.count()).isEqualTo(1);
    }
}
