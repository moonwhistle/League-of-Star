package com.sang.leagueofstar.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 프로젝트 전체에서 사용할 Redisson 공통 설정 클래스입니다.
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    private static final String REDISSON_HOST_PREFIX = "redis://";

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(REDISSON_HOST_PREFIX + host + ":" + port);
        if (StringUtils.hasText(password)) {
            singleServerConfig.setPassword(password);
        }

        return Redisson.create(config);
    }
}
