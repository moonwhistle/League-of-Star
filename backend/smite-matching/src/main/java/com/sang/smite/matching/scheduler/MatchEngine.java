package com.sang.smite.matching.scheduler;

import com.sang.smite.matching.service.MatchEngineService;
import com.sang.smite.matching.metrics.MatchEngineMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 매칭 엔진 워커(Worker)
 *
 * <p>주기적으로 전체 Redis 대기열 스냅샷을 인메모리로 로드한 뒤,
 * 가장 오래 대기한 유저(FIFO)부터 슬라이딩 윈도우 방식으로 상대를 찾아 페어링합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEngine {

    private static final String MATCH_ENGINE_LOCK_KEY = "lock:match:engine";
    private static final String MATCH_ENGINE_FIXED_DELAY_MS = "1000";
    private static final long LOCK_WAIT_TIME_SECONDS = 0L;
    private static final long LOCK_LEASE_TIME_SECONDS = 5L;

    private final RedissonClient redissonClient;
    private final MatchEngineService matchEngineService;
    private final MatchEngineMetrics matchEngineMetrics;

    /**
     * 매칭 엔진 스캔 루프를 주기적으로 실행합니다.
     *
     * <p>멀티 인스턴스 환경에서 중복 스캔을 막기 위해 Redis 전역 락을 먼저 획득합니다.
     * 락을 얻지 못한 인스턴스는 이번 사이클을 즉시 건너뛰고 다음 스케줄을 기다립니다.</p>
     */
    @Scheduled(fixedDelayString = MATCH_ENGINE_FIXED_DELAY_MS)
    public void processMatching() {
        RLock lock = redissonClient.getLock(MATCH_ENGINE_LOCK_KEY);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                matchEngineMetrics.incrementLockSkipped();
                log.debug("[MatchEngine] 다른 인스턴스가 스캔 중이므로 이번 사이클을 건너뜁니다.");
                return;
            }

            matchEngineService.processMatching();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[MatchEngine] 락 획득 대기 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
