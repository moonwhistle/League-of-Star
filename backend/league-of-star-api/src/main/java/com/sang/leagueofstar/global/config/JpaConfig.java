package com.sang.leagueofstar.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
        "com.sang.leagueofstar.domain",
        "com.sang.leagueofstar.game.record.outbox.repository"
})
public class JpaConfig {
}
