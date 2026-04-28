package com.sang.smite.global.config;

import com.sang.smite.global.annotation.RedisRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.context.annotation.ComponentScan.Filter;

@Configuration
@EnableRedisRepositories(
    basePackages = "com.sang.smite",
    includeFilters = @Filter(type = FilterType.ANNOTATION, classes = RedisRepository.class)
)
public class RedisRepositoryConfig {
}
