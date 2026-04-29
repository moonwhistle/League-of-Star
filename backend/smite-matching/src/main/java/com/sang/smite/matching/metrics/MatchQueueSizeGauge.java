package com.sang.smite.matching.metrics;

import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 티어별 매칭 대기열 현재 인원 수를 Gauge로 등록합니다.
 *
 * <p>Gauge는 Prometheus가 scrape 할 때마다 {@link MatchStore#findAll()}을 호출하여
 * 실시간 인원 수를 읽습니다. 별도 스케줄러는 필요하지 않습니다.
 *
 * <p><b>성능 참고:</b> {@code findAll()}은 Redis Batch(파이프라이닝)로 전체 ZSET을
 * 한 번에 조회하므로, Prometheus 기본 scrape interval(15초)에서는 부하가 낮습니다.
 * 트래픽이 매우 높은 경우 {@code MatchStore}에 {@code zcard(tier)} 메서드를 추가하여
 * Redis {@code ZCARD} 단일 명령으로 대체하는 것을 권장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchQueueSizeGauge {

    private final MeterRegistry meterRegistry;
    private final MatchStore matchStore;

    @PostConstruct
    public void registerGauges() {
        for (int tier = MatchingConstants.TIER_SCORE_MIN; tier <= MatchingConstants.TIER_SCORE_MAX; tier++) {
            final int tierScore = tier;

            Gauge.builder(MatchQueueMetrics.QUEUE_SIZE, matchStore,
                            store -> store.findAll().stream()
                                    .filter(t -> t.tierScore() == tierScore)
                                    .count()
                    )
                    .tag(MatchQueueMetrics.TAG_TIER, String.valueOf(tierScore))
                    .description("tier=" + tierScore + " 매칭 대기열 현재 인원 수")
                    .register(meterRegistry);
        }

        log.info("Registered match.queue.size Gauges for tier {} to {}",
                MatchingConstants.TIER_SCORE_MIN, MatchingConstants.TIER_SCORE_MAX);
    }
}
