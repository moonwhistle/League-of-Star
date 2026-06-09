package com.sang.leagueofstar.matching.infrastructure.redis;

import com.sang.leagueofstar.matching.TestMatchingApplication;
import com.sang.leagueofstar.redis.AbstractRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TestMatchingApplication.class)
@ActiveProfiles("test")
abstract class MatchingRedisIntegrationTest extends AbstractRedisTest {
}
