package com.sang.smite.matching.metrics;

import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 매칭 대기열 지표를 수집하는 AOP Aspect입니다.
 *
 * <p>{@link com.sang.smite.matching.service.MatchService}의 비즈니스 로직을
 * 전혀 수정하지 않고 메트릭을 투명하게 수집합니다.
 *
 * <h3>수집 지표</h3>
 * <ul>
 *   <li>joinQueue: Counter(tier, result) + Timer(tier, result) + Inflight Gauge</li>
 *   <li>leaveQueue: Counter(tier, result) + Timer(tier, result) + Inflight Gauge</li>
 * </ul>
 *
 * <h3>동시성 안전성</h3>
 * <p>Inflight 추적에 사용하는 {@link AtomicInteger}는 CAS(Compare-And-Swap) 기반으로
 * 외부 락 없이 스레드 안전을 보장합니다.
 */
@Slf4j
@Aspect
@Component
public class MatchQueueMetricsAspect {

    private final MeterRegistry meterRegistry;

    // 동시 처리 중인 요청 수 추적 (AtomicInteger → CAS 기반 스레드 안전)
    private final AtomicInteger joinInflight = new AtomicInteger(0);
    private final AtomicInteger leaveInflight = new AtomicInteger(0);

    public MatchQueueMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // In-flight Gauge 등록: Prometheus scrape 시 AtomicInteger 값을 폴링
        Gauge.builder(MatchQueueMetrics.JOIN_INFLIGHT, joinInflight, AtomicInteger::get)
                .description("현재 처리 중인 joinQueue 동시 요청 수")
                .register(meterRegistry);

        Gauge.builder(MatchQueueMetrics.LEAVE_INFLIGHT, leaveInflight, AtomicInteger::get)
                .description("현재 처리 중인 leaveQueue 동시 요청 수")
                .register(meterRegistry);
    }

    /**
     * joinQueue 지표 수집.
     *
     * <p>result 태그 값:
     * <ul>
     *   <li>success: 정상 진입 완료</li>
     *   <li>duplicate: SETNX 실패 (이미 대기 중 / 게임 중)</li>
     *   <li>error: Redis 오류</li>
     * </ul>
     */
    @Around("execution(* com.sang.smite.matching.service.MatchService.joinQueue(Long, int)) && args(userId, tierScore)")
    public Object recordJoinQueue(ProceedingJoinPoint pjp, Long userId, int tierScore) throws Throwable {
        String result = MatchQueueMetrics.RESULT_SUCCESS;
        String tier = String.valueOf(tierScore);

        joinInflight.incrementAndGet();
        long start = System.nanoTime();

        try {
            return pjp.proceed();

        } catch (MatchingException e) {
            result = (e.getErrorCode() == MatchingErrorCode.ALREADY_IN_QUEUE)
                    ? MatchQueueMetrics.RESULT_DUPLICATE
                    : MatchQueueMetrics.RESULT_ERROR;
            throw e;

        } catch (Exception e) {
            result = MatchQueueMetrics.RESULT_ERROR;
            throw e;

        } finally {
            long durationNs = System.nanoTime() - start;
            joinInflight.decrementAndGet();

            meterRegistry.counter(MatchQueueMetrics.JOIN_TOTAL,
                    MatchQueueMetrics.TAG_TIER, tier,
                    MatchQueueMetrics.TAG_RESULT, result
            ).increment();

            Timer.builder(MatchQueueMetrics.JOIN_DURATION)
                    .tag(MatchQueueMetrics.TAG_TIER, tier)
                    .tag(MatchQueueMetrics.TAG_RESULT, result)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(durationNs, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * leaveQueue 지표 수집.
     *
     * <p>result 태그 값:
     * <ul>
     *   <li>success: 정상 취소 완료</li>
     *   <li>not_in_queue: 대기 중 아닌 상태에서 취소 시도 또는 엔진이 이미 선점</li>
     *   <li>error: Redis 오류</li>
     * </ul>
     */
    @Around("execution(* com.sang.smite.matching.service.MatchService.leaveQueue(Long, int)) && args(userId, tierScore)")
    public Object recordLeaveQueue(ProceedingJoinPoint pjp, Long userId, int tierScore) throws Throwable {
        String result = MatchQueueMetrics.RESULT_SUCCESS;
        String tier = String.valueOf(tierScore);

        leaveInflight.incrementAndGet();
        long start = System.nanoTime();

        try {
            return pjp.proceed();

        } catch (MatchingException e) {
            result = (e.getErrorCode() == MatchingErrorCode.NOT_IN_QUEUE)
                    ? MatchQueueMetrics.RESULT_NOT_IN_QUEUE
                    : MatchQueueMetrics.RESULT_ERROR;
            throw e;

        } catch (Exception e) {
            result = MatchQueueMetrics.RESULT_ERROR;
            throw e;

        } finally {
            long durationNs = System.nanoTime() - start;
            leaveInflight.decrementAndGet();

            meterRegistry.counter(MatchQueueMetrics.LEAVE_TOTAL,
                    MatchQueueMetrics.TAG_TIER, tier,
                    MatchQueueMetrics.TAG_RESULT, result
            ).increment();

            Timer.builder(MatchQueueMetrics.LEAVE_DURATION)
                    .tag(MatchQueueMetrics.TAG_TIER, tier)
                    .tag(MatchQueueMetrics.TAG_RESULT, result)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(durationNs, TimeUnit.NANOSECONDS);
        }
    }
}
