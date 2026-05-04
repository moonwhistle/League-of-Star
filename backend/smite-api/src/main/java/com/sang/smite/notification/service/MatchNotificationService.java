package com.sang.smite.notification.service;

import com.sang.smite.notification.constants.MatchNotificationEventName;
import com.sang.smite.notification.domain.SseConnection;
import com.sang.smite.notification.dto.SseConnectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
public class MatchNotificationService {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final SseConnectionRegistry sseConnectionRegistry;
    private final Clock clock = Clock.systemUTC();

    public MatchNotificationService(SseConnectionRegistry sseConnectionRegistry) {
        this.sseConnectionRegistry = sseConnectionRegistry;
    }

    /**
     * 매칭 알림용 SSE 연결을 생성합니다.
     *
     * <p>같은 유저가 다시 연결하면 기존 연결은 종료하고 새 연결로 교체합니다.
     * heartbeat와 매칭 성사 이벤트 전송은 후속 task에서 확장합니다.</p>
     */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        SseConnection connection = new SseConnection(userId, emitter);

        sseConnectionRegistry.register(connection);
        sendConnectedEvent(connection);

        return emitter;
    }

    private void sendConnectedEvent(SseConnection connection) {
        try {
            connection.send(
                    MatchNotificationEventName.CONNECTED,
                    new SseConnectedEvent(connection.userId(), Instant.now(clock))
            );
        } catch (IOException e) {
            log.warn("[MatchNotification] SSE connected 이벤트 전송 실패: userId={}", connection.userId(), e);
            sseConnectionRegistry.remove(connection.userId(), connection);
            connection.completeWithError(e);
        }
    }
}
