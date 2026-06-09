package com.sang.leagueofstar.notification.sse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SseSchedulerConfig {

    private static final int NOTIFICATION_SCHEDULER_POOL_SIZE = 1;
    private static final String NOTIFICATION_THREAD_NAME_PREFIX = "notification-";

    @Bean
    public TaskScheduler notificationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(NOTIFICATION_SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix(NOTIFICATION_THREAD_NAME_PREFIX);
        scheduler.initialize();
        return scheduler;
    }
}
