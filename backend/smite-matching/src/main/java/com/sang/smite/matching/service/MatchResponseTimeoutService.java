package com.sang.smite.matching.service;

import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchTimeoutStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * 매칭 응답 timeout job의 처리 흐름을 담당하는 애플리케이션 서비스입니다.
 *
 * <p>pending ZSET에서 deadline이 지난 timeout job을 조회하고, Lua claim을 통해 processing ZSET으로
 * 원자 이동한 뒤 matchId lock 기반 timeout 정산을 호출합니다.</p>
 *
 * <p>정산이 성공하거나 이미 종료된 세션으로 no-op 처리되면 processing job을 ack로 제거합니다.
 * 정산 중 예외가 발생하면 ack하지 않고 processing lease 만료 후 reclaim으로 재처리되도록 둡니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchResponseTimeoutService {

    private final MatchTimeoutStore timeoutStore;
    private final MatchResponseProcessor matchResponseProcessor;
    private final Clock clock;

    /**
     * 만료된 processing job을 먼저 복구한 뒤, due pending job을 claim하여 timeout 정산을 시도합니다.
     */
    public void processTimeouts() {
        long nowMillis = clock.millis();
        reclaimExpiredProcessing(nowMillis);
        processDuePending(nowMillis);
    }

    /**
     * processing lease가 만료된 job을 pending으로 되돌려 다음 tick에서 재처리할 수 있게 합니다.
     */
    private void reclaimExpiredProcessing(long nowMillis) {
        List<String> expiredMatchIds = timeoutStore.findExpiredProcessing(
                nowMillis,
                MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE
        );

        for (String matchId : expiredMatchIds) {
            try {
                timeoutStore.reclaim(matchId, nowMillis, nowMillis);
            } catch (Exception e) {
                log.warn("Failed to reclaim match response timeout job: matchId={}", matchId, e);
            }
        }
    }

    /**
     * deadline이 지난 pending job을 조회하고 matchId별 timeout 처리를 시도합니다.
     */
    private void processDuePending(long nowMillis) {
        List<String> dueMatchIds = timeoutStore.findDuePending(
                nowMillis,
                MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE
        );

        for (String matchId : dueMatchIds) {
            processMatchTimeout(matchId, nowMillis);
        }
    }

    /**
     * 단일 matchId를 claim한 뒤 timeout 정산을 실행하고, 성공 또는 no-op이면 ack로 제거합니다.
     */
    private void processMatchTimeout(String matchId, long nowMillis) {
        long processingExpireAtMillis = nowMillis + MatchingConstants.TIMEOUT_PROCESSING_LEASE_MILLIS;
        try {
            boolean claimed = timeoutStore.claim(matchId, nowMillis, processingExpireAtMillis);
            if (!claimed) {
                return;
            }

            matchResponseProcessor.timeoutWithLock(matchId);
            timeoutStore.ack(matchId);
        } catch (Exception e) {
            log.warn("Failed to process match response timeout job: matchId={}", matchId, e);
        }
    }
}
