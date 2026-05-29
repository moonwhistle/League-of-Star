package com.sang.smite.matching.infrastructure.redis;

import com.sang.smite.matching.TestMatchingApplication;
import com.sang.smite.redis.AbstractRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TestMatchingApplication.class)
@ActiveProfiles("test")
abstract class MatchingRedisIntegrationTest extends AbstractRedisTest {
}
