package com.sang.smite.notification.sse.service;

import com.sang.smite.notification.match.constants.MatchNotificationEventName;
import com.sang.smite.notification.sse.connection.SseConnection;
import com.sang.smite.notification.sse.connection.SseConnectionRegistry;
import com.sang.smite.notification.sse.dto.SseHeartbeatEvent;
import com.sang.smite.notification.sse.sender.SseEventSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class SseHeartbeatService {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final SseConnectionRegistry sseConnectionRegistry;
    private final SseEventSender sseEventSender;
    private final TaskScheduler notificationTaskScheduler;
    private final Clock clock;
    private ScheduledFuture<?> heartbeatTask;

    public SseHeartbeatService(
            SseConnectionRegistry sseConnectionRegistry,
            SseEventSender sseEventSender,
            @Qualifier("notificationTaskScheduler") TaskScheduler notificationTaskScheduler,
            Clock clock
    ) {
        this.sseConnectionRegistry = sseConnectionRegistry;
        this.sseEventSender = sseEventSender;
        this.notificationTaskScheduler = notificationTaskScheduler;
        this.clock = clock;
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
            sseEventSender.send(connection, MatchNotificationEventName.HEARTBEAT, event);
        }
    }
}
