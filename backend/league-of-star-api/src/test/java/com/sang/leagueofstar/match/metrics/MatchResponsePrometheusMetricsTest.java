package com.sang.leagueofstar.match.metrics;

import com.sang.leagueofstar.matching.metrics.MatchResponseMetricNames;
import com.sang.leagueofstar.matching.metrics.MatchResponseMetrics;
import com.sang.leagueofstar.redis.AbstractRedisTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MatchResponsePrometheusMetricsTest extends AbstractRedisTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MatchResponseMetrics matchResponseMetrics;

    @Test
    @DisplayName("매칭 응답 지표가 actuator prometheus endpoint에 노출된다")
    void matchResponseMetricsExposedToPrometheus() throws Exception {
        matchResponseMetrics.incrementResponseAttempt(MatchResponseMetricNames.ACTION_ACCEPT);
        matchResponseMetrics.incrementTimeoutSettlement(MatchResponseMetricNames.OUTCOME_SUCCESS);
        matchResponseMetrics.incrementTimeoutReturnedUsers(1);
        matchResponseMetrics.recordTimeoutProcessingDelay(1_000L);

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("match_response_requests_total")))
                .andExpect(content().string(containsString("action=\"accept\"")))
                .andExpect(content().string(containsString("result=\"attempt\"")))
                .andExpect(content().string(containsString("match_response_timeout_settlements_total")))
                .andExpect(content().string(containsString("outcome=\"success\"")))
                .andExpect(content().string(containsString("match_response_timeout_queue_returned_users_total")))
                .andExpect(content().string(containsString("match_response_timeout_processing_delay_seconds_bucket")))
                .andExpect(content().string(containsString("match_response_timeout_pending_backlog")));
    }
}
