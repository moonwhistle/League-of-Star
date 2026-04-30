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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final int MIN_MATCHABLE_USER_COUNT = 2;
    private static final long WAIT_TIME_FIRST_STEP_SECONDS = 10L;
    private static final long WAIT_TIME_SECOND_STEP_SECONDS = 20L;
    private static final long WAIT_TIME_THIRD_STEP_SECONDS = 30L;
    private static final int TIER_DIFF_FIRST_STEP = 1;
    private static final int TIER_DIFF_SECOND_STEP = 2;
    private static final int TIER_DIFF_THIRD_STEP = 4;
    private static final int TIER_DIFF_MAX = 8;

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

            processMatchingWithLock();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[MatchEngine] 락 획득 대기 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void processMatchingWithLock() {
        log.debug("[MatchEngine] 스캔 루프 시작");

        List<MatchTicket> tickets = loadSortedTickets();
        if (tickets.size() < MIN_MATCHABLE_USER_COUNT) {
            log.debug("[MatchEngine] 매칭 가능 인원 부족, 현재 인원: {}명", tickets.size());
            return;
        }

        pairTickets(tickets);

        log.debug("[MatchEngine] 스캔 루프 완료");
    }

    private List<MatchTicket> loadSortedTickets() {
        List<MatchTicket> tickets = matchStore.findAll();
        tickets.sort(Comparator.comparingLong(MatchTicket::entryTime));

        log.debug("[MatchEngine] 대기열 스캔 완료, 현재 인원: {}명", tickets.size());
        return tickets;
    }

    private void pairTickets(List<MatchTicket> tickets) {
        long now = System.currentTimeMillis();
        Set<Long> pairedUserIds = new HashSet<>();

        for (int i = 0; i < tickets.size(); i++) {
            MatchTicket userA = tickets.get(i);
            if (pairedUserIds.contains(userA.userId())) {
                continue;
            }

            tryPair(userA, tickets, i + 1, now, pairedUserIds);
        }
    }

    private void tryPair(
            MatchTicket userA,
            List<MatchTicket> tickets,
            int candidateStartIndex,
            long now,
            Set<Long> pairedUserIds
    ) {
        for (int i = candidateStartIndex; i < tickets.size(); i++) {
            MatchTicket userB = tickets.get(i);
            if (pairedUserIds.contains(userB.userId()) || !isMatchable(userA, userB, now)) {
                continue;
            }

            boolean success = matchStore.atomicPairRemove(
                    userA.userId(), userA.tierScore(),
                    userB.userId(), userB.tierScore()
            );

            if (success) {
                markPaired(userA, userB, pairedUserIds);
                handleMatchedPair(userA, userB);
                return;
            }
        }
    }

    private boolean isMatchable(MatchTicket userA, MatchTicket userB, long now) {
        long waitTimeSeconds = (now - userA.entryTime()) / 1000;
        int allowedTierDiff = calculateAllowedTierDiff(waitTimeSeconds);
        int tierDiff = Math.abs(userA.tierScore() - userB.tierScore());

        return tierDiff <= allowedTierDiff;
    }

    private void markPaired(MatchTicket userA, MatchTicket userB, Set<Long> pairedUserIds) {
        pairedUserIds.add(userA.userId());
        pairedUserIds.add(userB.userId());
    }

    private void handleMatchedPair(MatchTicket userA, MatchTicket userB) {
        log.info("[MatchEngine] 매칭 성사: User {} (Tier {}) <-> User {} (Tier {})",
                userA.userId(), userA.tierScore(), userB.userId(), userB.tierScore());

        // TODO: 상태 변경 (FOUND), 수락 세션 생성 및 이벤트 발행
    }

    /**
     * 대기 시간에 따른 매칭 허용 티어 폭(Sliding Window)을 계산합니다.
     */
    private int calculateAllowedTierDiff(long waitTimeSec) {
        if (waitTimeSec <= WAIT_TIME_FIRST_STEP_SECONDS) {
            return TIER_DIFF_FIRST_STEP;
        }

        if (waitTimeSec <= WAIT_TIME_SECOND_STEP_SECONDS) {
            return TIER_DIFF_SECOND_STEP;
        }

        if (waitTimeSec <= WAIT_TIME_THIRD_STEP_SECONDS) {
            return TIER_DIFF_THIRD_STEP;
        }

        return TIER_DIFF_MAX;
    }
}
