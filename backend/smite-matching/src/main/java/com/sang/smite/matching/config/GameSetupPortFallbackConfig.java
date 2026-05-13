package com.sang.smite.matching.config;

import com.sang.smite.matching.domain.port.GameSetupPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameSetupPortFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(GameSetupPort.class)
    public GameSetupPort unsupportedGameSetupPort() {
        return (firstUserId, secondUserId) -> {
            throw new UnsupportedOperationException("Game setup port is not configured.");
        };
    }
}
