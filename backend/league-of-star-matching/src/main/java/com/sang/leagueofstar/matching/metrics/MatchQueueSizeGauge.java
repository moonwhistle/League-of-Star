package com.sang.leagueofstar.matching.metrics;

import com.sang.leagueofstar.matching.repository.MatchQueueStore;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FIFO 매칭 대기열의 현재 인원 수를 Gauge로 등록합니다.
 *
 * <p>Gauge는 Prometheus가 scrape 할 때마다 단일 Redis ZSET 크기만 조회합니다.
 * 별도 스케줄러는 필요하지 않습니다.
 *
 * <p><b>성능 참고:</b> 전체 티켓을 가져오지 않고 {@link MatchQueueStore#count()}만
 * 호출하여 모니터링이 매칭 엔진 스캔 비용에 영향을 주지 않도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchQueueSizeGauge {

    private final MeterRegistry meterRegistry;
    private final MatchQueueStore matchStore;
    private final MatchJobStore matchJobStore;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder(MatchQueueMetrics.QUEUE_SIZE, matchStore, MatchQueueStore::count)
                .description("FIFO 매칭 대기열 현재 인원 수")
                .register(meterRegistry);
        Gauge.builder(MatchQueueMetrics.PROCESSING_SIZE, matchJobStore, MatchJobStore::pendingCount)
                .description("Consumer Group PEL의 미완료 MatchJob 수")
                .register(meterRegistry);

        log.info("Registered FIFO waiting and Stream PEL gauges");
    }
}
