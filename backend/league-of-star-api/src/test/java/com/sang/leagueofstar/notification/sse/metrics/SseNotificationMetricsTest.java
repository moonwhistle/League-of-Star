package com.sang.leagueofstar.notification.sse.metrics;

import com.sang.leagueofstar.notification.match.constants.MatchNotificationEventName;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SseNotificationMetricsTest {

    private static final String TEST_EVENT = MatchNotificationEventName.MATCH_FOUND;

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
        metrics.incrementSendAttempt(TEST_EVENT);
        metrics.recordSendSuccess(TEST_EVENT, metrics.startSendTimer());
        metrics.recordSendFailure(TEST_EVENT, metrics.startSendTimer());

        // then
        Counter attempts = meterRegistry.get(SseNotificationMetricNames.EVENTS_SEND_ATTEMPTS)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .counter();
        Counter success = meterRegistry.get(SseNotificationMetricNames.EVENTS_SEND_SUCCESS)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .counter();
        Counter failures = meterRegistry.get(SseNotificationMetricNames.EVENTS_SEND_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .counter();

        assertThat(attempts.count()).isEqualTo(1.0);
        assertThat(success.count()).isEqualTo(1.0);
        assertThat(failures.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("이벤트 전송 지연 Timer를 결과 태그와 함께 기록한다.")
    void recordSendDuration() {
        // when
        metrics.recordSendSuccess(TEST_EVENT, metrics.startSendTimer());
        metrics.recordSendFailure(TEST_EVENT, metrics.startSendTimer());

        // then
        Timer successTimer = meterRegistry.get(SseNotificationMetricNames.EVENT_SEND_DURATION)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .tag(SseNotificationMetricNames.TAG_RESULT, SseNotificationMetricNames.RESULT_SUCCESS)
                .timer();
        Timer failureTimer = meterRegistry.get(SseNotificationMetricNames.EVENT_SEND_DURATION)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .tag(SseNotificationMetricNames.TAG_RESULT, SseNotificationMetricNames.RESULT_FAILURE)
                .timer();

        assertThat(successTimer.count()).isEqualTo(1);
        assertThat(failureTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Pub/Sub publish 성공/실패 카운터를 증가시킨다.")
    void incrementPubSubPublishCounters() {
        // when
        metrics.incrementPubSubPublishSuccess(TEST_EVENT);
        metrics.incrementPubSubPublishFailure(TEST_EVENT);

        // then
        Counter success = meterRegistry.get(SseNotificationMetricNames.PUBSUB_PUBLISH_SUCCESS)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .counter();
        Counter failure = meterRegistry.get(SseNotificationMetricNames.PUBSUB_PUBLISH_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .counter();

        assertThat(success.count()).isEqualTo(1.0);
        assertThat(failure.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Pub/Sub subscribe 수신/실패 카운터를 증가시킨다.")
    void incrementPubSubSubscribeCounters() {
        // when
        metrics.incrementPubSubMessageReceived(TEST_EVENT);
        metrics.incrementPubSubMessageFailure(TEST_EVENT, SseNotificationMetricNames.REASON_DECODE);
        metrics.incrementPubSubMessageFailure(TEST_EVENT, SseNotificationMetricNames.REASON_DISPATCH);

        // then
        Counter received = meterRegistry.get(SseNotificationMetricNames.PUBSUB_MESSAGES_RECEIVED)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .counter();
        Counter decodeFailure = meterRegistry.get(SseNotificationMetricNames.PUBSUB_MESSAGES_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .tag(SseNotificationMetricNames.TAG_REASON, SseNotificationMetricNames.REASON_DECODE)
                .counter();
        Counter dispatchFailure = meterRegistry.get(SseNotificationMetricNames.PUBSUB_MESSAGES_FAILURES)
                .tag(SseNotificationMetricNames.TAG_EVENT, TEST_EVENT)
                .tag(SseNotificationMetricNames.TAG_REASON, SseNotificationMetricNames.REASON_DISPATCH)
                .counter();

        assertThat(received.count()).isEqualTo(1.0);
        assertThat(decodeFailure.count()).isEqualTo(1.0);
        assertThat(dispatchFailure.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("match_found 로컬 dispatch hit/miss 카운터를 증가시킨다.")
    void incrementMatchFoundDispatchCounters() {
        // when
        metrics.incrementMatchFoundDispatchLocalHit();
        metrics.incrementMatchFoundDispatchLocalMiss();

        // then
        Counter hit = meterRegistry.get(SseNotificationMetricNames.MATCH_FOUND_DISPATCH_LOCAL_HITS).counter();
        Counter miss = meterRegistry.get(SseNotificationMetricNames.MATCH_FOUND_DISPATCH_LOCAL_MISSES).counter();

        assertThat(hit.count()).isEqualTo(1.0);
        assertThat(miss.count()).isEqualTo(1.0);
    }
}
