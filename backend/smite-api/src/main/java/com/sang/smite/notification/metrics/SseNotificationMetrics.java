package com.sang.smite.notification.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * SSE 매칭 알림의 성능 지표 기록을 캡슐화합니다.
 */
@Component
public class SseNotificationMetrics {

    private final MeterRegistry meterRegistry;

    public SseNotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public static SseNotificationMetrics noop() {
        return new SseNotificationMetrics(new SimpleMeterRegistry());
    }

    public void registerActiveConnectionGauge(Supplier<Number> activeConnectionCount) {
        meterRegistry.gauge(
                SseNotificationMetricNames.CONNECTIONS_ACTIVE,
                activeConnectionCount,
                supplier -> supplier.get().doubleValue()
        );
    }

    public void incrementConnectionOpened() {
        meterRegistry.counter(SseNotificationMetricNames.CONNECTIONS_OPENED).increment();
    }

    public void incrementConnectionClosed(String reason) {
        meterRegistry.counter(
                SseNotificationMetricNames.CONNECTIONS_CLOSED,
                SseNotificationMetricNames.TAG_REASON,
                reason
        ).increment();
    }

    public void recordConnectionDuration(Duration duration) {
        Timer.builder(SseNotificationMetricNames.CONNECTION_DURATION)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
    }

    public Timer.Sample startSendTimer() {
        return Timer.start(meterRegistry);
    }

    public void incrementSendAttempt(String eventName) {
        meterRegistry.counter(
                SseNotificationMetricNames.EVENTS_SEND_ATTEMPTS,
                SseNotificationMetricNames.TAG_EVENT,
                eventName
        ).increment();
    }

    public void recordSendSuccess(String eventName, Timer.Sample sample) {
        meterRegistry.counter(
                SseNotificationMetricNames.EVENTS_SEND_SUCCESS,
                SseNotificationMetricNames.TAG_EVENT,
                eventName
        ).increment();
        recordSendDuration(eventName, "success", sample);
    }

    public void recordSendFailure(String eventName, Timer.Sample sample) {
        meterRegistry.counter(
                SseNotificationMetricNames.EVENTS_SEND_FAILURES,
                SseNotificationMetricNames.TAG_EVENT,
                eventName
        ).increment();
        recordSendDuration(eventName, "failure", sample);
    }

    private void recordSendDuration(String eventName, String result, Timer.Sample sample) {
        sample.stop(Timer.builder(SseNotificationMetricNames.EVENT_SEND_DURATION)
                .tag(SseNotificationMetricNames.TAG_EVENT, eventName)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }
}
