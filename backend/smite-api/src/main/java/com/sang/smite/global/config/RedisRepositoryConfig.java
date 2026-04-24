package com.sang.smite.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableRedisRepositories(basePackages = "com.sang.smite.redis")
public class RedisRepositoryConfig {
}
