package com.sang.smite.notification.service;

import com.sang.smite.notification.constants.MatchNotificationEventName;
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

    private final Clock clock;

    public MatchNotificationService() {
        this(Clock.systemUTC());
    }

    MatchNotificationService(Clock clock) {
        this.clock = clock;
    }

    /**
     * 매칭 알림용 SSE 연결을 생성합니다.
     *
     * <p>V1에서는 연결 API와 초기 연결 이벤트 전송까지만 담당합니다.
     * 유저별 연결 저장, 재연결 교체, heartbeat, 실패 연결 정리는 후속 task에서 확장합니다.</p>
     */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        sendConnectedEvent(userId, emitter);
        return emitter;
    }

    private void sendConnectedEvent(Long userId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name(MatchNotificationEventName.CONNECTED)
                    .data(new SseConnectedEvent(userId, Instant.now(clock))));
        } catch (IOException e) {
            log.warn("[MatchNotification] SSE connected 이벤트 전송 실패: userId={}", userId, e);
            emitter.completeWithError(e);
        }
    }
}
