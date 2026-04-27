package com.sang.smite.redis;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest(classes = TestRedisApplication.class)
@ActiveProfiles("test")
public abstract class AbstractRedisRepositoryTest {

    public static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>("redis:7.2.4-alpine")
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());

    static {
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }
}
