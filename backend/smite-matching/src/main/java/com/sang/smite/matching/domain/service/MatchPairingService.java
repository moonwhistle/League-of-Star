package com.sang.smite.matching.domain.service;

import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.matching.metrics.MatchEngineMetrics;
import com.sang.smite.matching.repository.MatchQueueStore;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 매칭 엔진의 실제 페어링 로직을 수행하는 서비스입니다.
 *
 * <p>{@link com.sang.smite.matching.scheduler.MatchEngineScheduler}은 스케줄링과 전역 락만 담당하고,
 * 이 클래스는 Redis 대기열 조회, FIFO 정렬, 슬라이딩 윈도우 기반 후보 탐색,
 * 원자적 큐 제거까지의 매칭 계산 흐름을 담당합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchPairingService {

    private static final int MIN_MATCHABLE_USER_COUNT = 2;
    private static final long WAIT_TIME_FIRST_STEP_SECONDS = 10L;
    private static final long WAIT_TIME_SECOND_STEP_SECONDS = 20L;
    private static final long WAIT_TIME_THIRD_STEP_SECONDS = 30L;
    private static final int TIER_DIFF_FIRST_STEP = 1;
    private static final int TIER_DIFF_SECOND_STEP = 2;
    private static final int TIER_DIFF_THIRD_STEP = 4;
    private static final int TIER_DIFF_MAX = 8;

    private final MatchQueueStore matchStore;
    private final MatchFoundService matchFoundService;
    private final MatchEngineMetrics matchEngineMetrics;
    private final Clock clock;

    /**
     * 전체 매칭 대기열을 스캔하여 조건에 맞는 유저 쌍을 매칭합니다.
     *
     * <p>이 메서드는 이미 전역 분산 락을 획득한 상태에서 호출된다는 전제를 가집니다.
     * 매칭 성사 후 후처리 실패는 로그로 남기고 다음 페어링 후보 처리를 계속합니다.</p>
     */
    public void processMatching() {
        Timer.Sample sample = matchEngineMetrics.startScanTimer();
        int pairedCount = 0;
        log.debug("[MatchEngineScheduler] 스캔 루프 시작");

        try {
            List<MatchTicket> tickets = loadSortedTickets();
            matchEngineMetrics.recordScannedTickets(tickets.size());

            if (tickets.size() < MIN_MATCHABLE_USER_COUNT) {
                log.debug("[MatchEngineScheduler] 매칭 가능 인원 부족, 현재 인원: {}명", tickets.size());
                return;
            }

            pairedCount = pairTickets(tickets);

            log.debug("[MatchEngineScheduler] 스캔 루프 완료");
        } finally {
            matchEngineMetrics.recordPairsPerScan(pairedCount);
            matchEngineMetrics.recordScanDuration(sample);
        }
    }

    private List<MatchTicket> loadSortedTickets() {
        List<MatchTicket> tickets = matchStore.findAll();
        tickets.sort(Comparator.comparingLong(MatchTicket::entryTime));

        log.debug("[MatchEngineScheduler] 대기열 스캔 완료, 현재 인원: {}명", tickets.size());
        return tickets;
    }

    private int pairTickets(List<MatchTicket> tickets) {
        long now = clock.millis();
        Set<Long> pairedUserIds = new HashSet<>();
        int pairedCount = 0;

        for (int i = 0; i < tickets.size(); i++) {
            MatchTicket userA = tickets.get(i);
            if (pairedUserIds.contains(userA.userId())) {
                continue;
            }

            if (tryPair(userA, tickets, i + 1, now, pairedUserIds)) {
                pairedCount++;
            }
        }

        return pairedCount;
    }

    private boolean tryPair(
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

            matchEngineMetrics.incrementAtomicPairAttempts();
            boolean success = matchStore.atomicPairRemove(
                    userA.userId(), userA.tierScore(),
                    userB.userId(), userB.tierScore()
            );

            if (success) {
                markPaired(userA, userB, pairedUserIds);
                handleMatchedPair(userA, userB);
                return true;
            }

            matchEngineMetrics.incrementAtomicPairFailures();
        }

        return false;
    }

    private boolean isMatchable(MatchTicket userA, MatchTicket userB, long now) {
        long waitTimeSeconds = userA.getWaitTimeSeconds(now);
        int allowedTierDiff = calculateAllowedTierDiff(waitTimeSeconds);
        int tierDiff = Math.abs(userA.tierScore() - userB.tierScore());

        return tierDiff <= allowedTierDiff;
    }

    private void markPaired(MatchTicket userA, MatchTicket userB, Set<Long> pairedUserIds) {
        pairedUserIds.add(userA.userId());
        pairedUserIds.add(userB.userId());
    }

    private void handleMatchedPair(MatchTicket userA, MatchTicket userB) {
        log.info("[MatchEngineScheduler] 매칭 성사: User {} (Tier {}) <-> User {} (Tier {})",
                userA.userId(), userA.tierScore(), userB.userId(), userB.tierScore());

        long matchedAt = clock.millis();
        matchEngineMetrics.incrementPairs();
        recordMatchedUserWait(userA, matchedAt);
        recordMatchedUserWait(userB, matchedAt);

        try {
            matchFoundService.process(userA, userB);
        } catch (Exception e) {
            log.error("[MatchEngineScheduler] 매칭 성사 후처리 실패: userA={}, userB={}",
                    userA.userId(), userB.userId(), e);
        }
    }

    private void recordMatchedUserWait(MatchTicket user, long now) {
        long waitMillis = Math.max(0L, now - user.entryTime());
        matchEngineMetrics.recordMatchedUserWait(waitMillis);
    }

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
