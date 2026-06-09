package com.sang.leagueofstar.matching.metrics;

import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.repository.MatchQueueStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 티어별 매칭 대기열 현재 인원 수를 Gauge로 등록합니다.
 *
 * <p>Gauge는 Prometheus가 scrape 할 때마다 티어별 Redis ZSET 크기만 조회합니다.
 * 별도 스케줄러는 필요하지 않습니다.
 *
 * <p><b>성능 참고:</b> 전체 티켓을 가져오지 않고 {@link MatchQueueStore#countByTierScore(int)}만
 * 호출하여 모니터링이 매칭 엔진 스캔 비용에 영향을 주지 않도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchQueueSizeGauge {

    private final MeterRegistry meterRegistry;
    private final MatchQueueStore matchStore;

    @PostConstruct
    public void registerGauges() {
        for (int tier = MatchingConstants.TIER_SCORE_MIN; tier <= MatchingConstants.TIER_SCORE_MAX; tier++) {
            final int tierScore = tier;

            Gauge.builder(MatchQueueMetrics.QUEUE_SIZE, matchStore,
                            store -> store.countByTierScore(tierScore)
                    )
                    .tag(MatchQueueMetrics.TAG_TIER, String.valueOf(tierScore))
                    .description("tier=" + tierScore + " 매칭 대기열 현재 인원 수")
                    .register(meterRegistry);
        }

        log.info("Registered match.queue.size Gauges for tier {} to {}",
                MatchingConstants.TIER_SCORE_MIN, MatchingConstants.TIER_SCORE_MAX);
    }
}
