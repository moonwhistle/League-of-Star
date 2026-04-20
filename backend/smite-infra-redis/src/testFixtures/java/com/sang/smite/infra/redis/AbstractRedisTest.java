package com.sang.smite.infra.redis;

import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers를 사용해 Redis 컨테이너를 자동으로 관리하는 베이스 클래스입니다.
 * java-test-fixtures 플러그인을 통해 여러 모듈에서 공유됩니다.
 */
public abstract class AbstractRedisTest {

    private static final String REDIS_IMAGE = "redis:7.2-alpine";
    private static final RedisContainer REDIS_CONTAINER;

    static {
        REDIS_CONTAINER = new RedisContainer(DockerImageName.parse(REDIS_IMAGE));
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }
}
