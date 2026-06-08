package com.sang.leagueofstar.notification.sse.sender;

import com.sang.leagueofstar.notification.sse.connection.SseConnection;
import com.sang.leagueofstar.notification.sse.connection.SseConnectionRegistry;
import com.sang.leagueofstar.notification.sse.metrics.SseNotificationMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SSE 이벤트 전송과 실패 연결 정리를 담당합니다.
 */
@Slf4j
@Component
public class SseEventSender {

    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseNotificationMetrics sseNotificationMetrics;

    public SseEventSender(
            SseConnectionRegistry sseConnectionRegistry,
            SseNotificationMetrics sseNotificationMetrics
    ) {
        this.sseConnectionRegistry = sseConnectionRegistry;
        this.sseNotificationMetrics = sseNotificationMetrics;
    }

    public boolean send(SseConnection connection, String eventName, Object payload) {
        sseNotificationMetrics.incrementSendAttempt(eventName);
        Timer.Sample sample = sseNotificationMetrics.startSendTimer();

        try {
            connection.send(eventName, payload);
            sseNotificationMetrics.recordSendSuccess(eventName, sample);
            return true;
        } catch (IOException e) {
            sseNotificationMetrics.recordSendFailure(eventName, sample);
            log.warn("[SSE] 이벤트 전송 실패. userId={}, event={}", connection.userId(), eventName, e);
            sseConnectionRegistry.removeBySendFailure(connection.userId(), connection);
            connection.completeWithError(e);
            return false;
        }
    }
}
