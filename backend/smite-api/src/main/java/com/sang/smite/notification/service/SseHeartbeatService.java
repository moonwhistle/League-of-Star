package com.sang.smite.notification.service;

import com.sang.smite.notification.constants.MatchNotificationEventName;
import com.sang.smite.notification.domain.SseConnection;
import com.sang.smite.notification.dto.SseHeartbeatEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class SseHeartbeatService {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final SseConnectionRegistry sseConnectionRegistry;
    private final TaskScheduler notificationTaskScheduler;
    private final Clock clock = Clock.systemUTC();
    private ScheduledFuture<?> heartbeatTask;

    public SseHeartbeatService(
            SseConnectionRegistry sseConnectionRegistry,
            @Qualifier("notificationTaskScheduler") TaskScheduler notificationTaskScheduler
    ) {
        this.sseConnectionRegistry = sseConnectionRegistry;
        this.notificationTaskScheduler = notificationTaskScheduler;
    }

    @PostConstruct
    public void start() {
        heartbeatTask = notificationTaskScheduler.scheduleWithFixedDelay(this::sendHeartbeat, HEARTBEAT_INTERVAL);
        log.info("[SSE] heartbeat 스케줄러 시작. interval={}s", HEARTBEAT_INTERVAL.toSeconds());
    }

    @PreDestroy
    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
    }

    void sendHeartbeat() {
        SseHeartbeatEvent event = new SseHeartbeatEvent(Instant.now(clock));

        for (SseConnection connection : sseConnectionRegistry.findAll()) {
            try {
                connection.send(MatchNotificationEventName.HEARTBEAT, event);
            } catch (IOException e) {
                log.warn("[SSE] heartbeat 전송 실패. userId={}", connection.userId(), e);
                sseConnectionRegistry.remove(connection.userId(), connection);
                connection.completeWithError(e);
            }
        }
    }
}
