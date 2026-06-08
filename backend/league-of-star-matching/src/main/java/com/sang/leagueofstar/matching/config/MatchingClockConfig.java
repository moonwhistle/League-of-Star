package com.sang.leagueofstar.matching.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * matching 모듈에서 사용하는 시간 기준 설정입니다.
 */
@Configuration
public class MatchingClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemUTC();
    }
}
