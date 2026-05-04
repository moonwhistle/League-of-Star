package com.sang.smite.notification.service;

import com.sang.smite.notification.domain.SseConnection;
import com.sang.smite.notification.metrics.SseNotificationMetricNames;
import com.sang.smite.notification.metrics.SseNotificationMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 유저별 SSE 연결을 관리합니다.
 */
@Slf4j
@Component
public class SseConnectionRegistry {

    private final ConcurrentMap<Long, SseConnection> connections = new ConcurrentHashMap<>();
    private final SseNotificationMetrics metrics;

    public SseConnectionRegistry(SseNotificationMetrics metrics) {
        this.metrics = metrics;
        this.metrics.registerActiveConnectionGauge(this::count);
    }

    public void register(SseConnection connection) {
        Long userId = connection.userId();
        SseConnection previous = connections.put(userId, connection);

        if (previous != null) {
            log.info("[SSE] 기존 연결을 새 연결로 교체합니다. userId={}", userId);
            metrics.incrementConnectionClosed(SseNotificationMetricNames.REASON_REPLACED);
            metrics.recordConnectionDuration(Duration.between(previous.connectedAt(), Instant.now()));
            previous.complete();
        }

        metrics.incrementConnectionOpened();

        connection.emitter().onCompletion(() -> remove(userId, connection, SseNotificationMetricNames.REASON_COMPLETION));
        connection.emitter().onTimeout(() -> {
            log.info("[SSE] 연결 타임아웃. userId={}", userId);
            remove(userId, connection, SseNotificationMetricNames.REASON_TIMEOUT);
            connection.complete();
        });
        connection.emitter().onError(throwable -> {
            log.warn("[SSE] 연결 에러. userId={}", userId, throwable);
            remove(userId, connection, SseNotificationMetricNames.REASON_ERROR);
        });

        log.info("[SSE] 연결 등록 완료. userId={}", userId);
    }

    public Optional<SseConnection> findByUserId(Long userId) {
        return Optional.ofNullable(connections.get(userId));
    }

    public Collection<SseConnection> findAll() {
        return List.copyOf(connections.values());
    }

    public void remove(Long userId, SseConnection connection) {
        remove(userId, connection, SseNotificationMetricNames.REASON_COMPLETION);
    }

    public void removeBySendFailure(Long userId, SseConnection connection) {
        remove(userId, connection, SseNotificationMetricNames.REASON_SEND_FAILURE);
    }

    private void remove(Long userId, SseConnection connection, String reason) {
        if (connections.remove(userId, connection)) {
            metrics.incrementConnectionClosed(reason);
            metrics.recordConnectionDuration(Duration.between(connection.connectedAt(), Instant.now()));
            log.info("[SSE] 연결 제거 완료. userId={}", userId);
        }
    }

    public int count() {
        return connections.size();
    }
}
