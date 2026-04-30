package com.sang.smite.matching.scheduler;

import com.sang.smite.domain.match.domain.vo.MatchTicket;
import com.sang.smite.matching.repository.MatchStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
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
    private static final long LOCK_LEASE_TIME_SECONDS = 2L;

    private final RedissonClient redissonClient;
    private final MatchStore matchStore;

    /**
     * 매칭 엔진 스캔 루프 (1초마다 반복)
     */
    @Scheduled(fixedDelayString = MATCH_ENGINE_FIXED_DELAY_MS)
    public void processMatching() {
        RLock lock = redissonClient.getLock(MATCH_ENGINE_LOCK_KEY);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("[MatchEngine] 다른 인스턴스가 스캔 중이므로 이번 사이클을 건너뜁니다.");
                return;
            }

            log.debug("[MatchEngine] 스캔 루프 시작");

            // 1. 전체 대기열 조회
            List<MatchTicket> tickets = matchStore.findAll();
            
            if (tickets.size() < 2) {
                return; // 매칭을 위한 최소 인원 부족
            }

            // 2. FIFO(entryTime 오름차순) 정렬: 가장 오래 대기한 유저에게 우선권 부여
            tickets.sort(Comparator.comparingLong(MatchTicket::entryTime));
            
            log.debug("[MatchEngine] 대기열 스캔 완료, 현재 인원: {}명", tickets.size());

            // TODO 3: 슬라이딩 윈도우 페어링 및 atomicPairRemove 연동

            // TODO 4: 매칭 성사 시 상태 변경 (FOUND) 및 수락 세션 생성

            log.debug("[MatchEngine] 스캔 루프 완료");
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
