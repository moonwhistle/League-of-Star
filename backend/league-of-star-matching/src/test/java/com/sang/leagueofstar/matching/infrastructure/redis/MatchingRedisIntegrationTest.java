package com.sang.leagueofstar.matching.infrastructure.redis;

import com.sang.leagueofstar.matching.TestMatchingApplication;
import com.sang.leagueofstar.matching.domain.service.MatchClaimRecoveryService;
import com.sang.leagueofstar.matching.domain.service.MatchPairingService;
import com.sang.leagueofstar.redis.AbstractRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        classes = TestMatchingApplication.class,
        properties = "matching.stream.consumer.enabled=false"
)
@ActiveProfiles("test")
abstract class MatchingRedisIntegrationTest extends AbstractRedisTest {

    @MockitoBean
    private MatchPairingService matchPairingService;

    @MockitoBean
    private MatchClaimRecoveryService matchClaimRecoveryService;
}
