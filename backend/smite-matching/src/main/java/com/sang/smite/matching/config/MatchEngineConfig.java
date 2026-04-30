package com.sang.smite.matching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 매칭 엔진용 스케줄러 설정
 */
@Configuration
@EnableScheduling
public class MatchEngineConfig implements SchedulingConfigurer {

    private static final int MATCH_ENGINE_SCHEDULER_POOL_SIZE = 2;
    private static final String MATCH_ENGINE_THREAD_NAME_PREFIX = "match-engine-";

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(matchEngineTaskScheduler());
    }

    /**
     * 매칭 엔진 작업이 다른 스케줄 작업과 실행 풀을 공유하지 않도록
     * 전용 ThreadPoolTaskScheduler를 등록합니다.
     */
    @Bean
    public TaskScheduler matchEngineTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(MATCH_ENGINE_SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix(MATCH_ENGINE_THREAD_NAME_PREFIX);
        scheduler.initialize();
        return scheduler;
    }
}
