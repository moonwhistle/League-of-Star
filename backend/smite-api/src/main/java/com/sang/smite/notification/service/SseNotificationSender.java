package com.sang.smite.notification.service;

import com.sang.smite.notification.domain.SseConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SSE 이벤트 전송과 실패 연결 정리를 담당합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotificationSender {

    private final SseConnectionRegistry sseConnectionRegistry;

    public boolean send(SseConnection connection, String eventName, Object payload) {
        try {
            connection.send(eventName, payload);
            return true;
        } catch (IOException e) {
            log.warn("[SSE] 이벤트 전송 실패. userId={}, event={}", connection.userId(), eventName, e);
            sseConnectionRegistry.remove(connection.userId(), connection);
            connection.completeWithError(e);
            return false;
        }
    }
}
