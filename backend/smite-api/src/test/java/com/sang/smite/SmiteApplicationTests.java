package com.sang.smite;

import com.sang.smite.redis.AbstractRedisTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class SmiteApplicationTests extends AbstractRedisTest {

	@Test
	void contextLoads() {
	}

}
