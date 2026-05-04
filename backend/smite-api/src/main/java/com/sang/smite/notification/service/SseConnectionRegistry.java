package com.sang.smite.notification.service;

import com.sang.smite.notification.domain.SseConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    public void register(SseConnection connection) {
        Long userId = connection.userId();
        SseConnection previous = connections.put(userId, connection);

        if (previous != null) {
            log.info("[SSE] 기존 연결을 새 연결로 교체합니다. userId={}", userId);
            previous.complete();
        }

        connection.emitter().onCompletion(() -> remove(userId, connection));
        connection.emitter().onTimeout(() -> {
            log.info("[SSE] 연결 타임아웃. userId={}", userId);
            remove(userId, connection);
            connection.complete();
        });
        connection.emitter().onError(throwable -> {
            log.warn("[SSE] 연결 에러. userId={}", userId, throwable);
            remove(userId, connection);
        });

        log.info("[SSE] 연결 등록 완료. userId={}", userId);
    }

    public Optional<SseConnection> findByUserId(Long userId) {
        return Optional.ofNullable(connections.get(userId));
    }

    public void remove(Long userId, SseConnection connection) {
        if (connections.remove(userId, connection)) {
            log.info("[SSE] 연결 제거 완료. userId={}", userId);
        }
    }

    public int count() {
        return connections.size();
    }
}
