package com.sang.leagueofstar;

import com.sang.leagueofstar.redis.AbstractRedisTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class LeagueOfStarApplicationTests extends AbstractRedisTest {

	@Test
	void contextLoads() {
	}

}
