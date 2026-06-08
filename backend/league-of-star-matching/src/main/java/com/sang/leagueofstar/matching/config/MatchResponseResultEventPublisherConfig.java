package com.sang.leagueofstar.matching.config;

import com.sang.leagueofstar.matching.domain.event.MatchResponseResultEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 매칭 응답 결과 이벤트 publisher의 기본 구현을 제공합니다.
 */
@Configuration
public class MatchResponseResultEventPublisherConfig {

    @Bean
    @ConditionalOnMissingBean(MatchResponseResultEventPublisher.class)
    public MatchResponseResultEventPublisher noopMatchResponseResultEventPublisher() {
        return event -> {
        };
    }
}
