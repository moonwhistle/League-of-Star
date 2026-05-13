package com.sang.smite.matching.config;

import com.sang.smite.matching.domain.port.GameSetupPort;
import com.sang.smite.matching.domain.result.GameSetupResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameSetupPortFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(GameSetupPort.class)
    public GameSetupPort unsupportedGameSetupPort() {
        return new GameSetupPort() {
            @Override
            public GameSetupResult setup(Long firstUserId, Long secondUserId) {
                throw new UnsupportedOperationException("Game setup port is not configured.");
            }

            @Override
            public void abort(Long gameRoomId) {
                throw new UnsupportedOperationException("Game setup port is not configured.");
            }
        };
    }
}
