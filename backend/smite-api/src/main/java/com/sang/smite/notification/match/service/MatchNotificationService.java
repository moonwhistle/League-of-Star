package com.sang.smite.notification.match.service;

import com.sang.smite.notification.match.constants.MatchNotificationEventName;
import com.sang.smite.notification.sse.connection.SseConnection;
import com.sang.smite.notification.sse.dto.SseConnectedEvent;
import com.sang.smite.notification.sse.connection.SseConnectionRegistry;
import com.sang.smite.notification.sse.sender.SseEventSender;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Instant;

@Service
public class MatchNotificationService {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseEventSender sseEventSender;
    private final Clock clock;

    public MatchNotificationService(
            SseConnectionRegistry sseConnectionRegistry,
            SseEventSender sseEventSender,
            Clock clock
    ) {
        this.sseConnectionRegistry = sseConnectionRegistry;
        this.sseEventSender = sseEventSender;
        this.clock = clock;
    }

    /**
     * 매칭 알림용 SSE 연결을 생성합니다.
     *
     * <p>같은 유저가 다시 연결하면 기존 연결은 종료하고 새 연결로 교체합니다.
     * 연결 이후 heartbeat, match_found, match_response_result 이벤트는 동일한 SSE 연결로 전달됩니다.</p>
     */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        SseConnection connection = new SseConnection(userId, emitter);

        sseConnectionRegistry.register(connection);
        sendConnectedEvent(connection);

        return emitter;
    }

    private void sendConnectedEvent(SseConnection connection) {
        sseEventSender.send(
                connection,
                MatchNotificationEventName.CONNECTED,
                new SseConnectedEvent(connection.userId(), Instant.now(clock))
        );
    }
}
