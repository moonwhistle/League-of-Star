package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.metrics.MatchEngineMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * 신규 수신과 PEL 복구가 공유하는 MatchJob 후처리 진입점입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchJobProcessor {

    private final MatchFoundService matchFoundService;
    private final MatchEngineMetrics matchEngineMetrics;
    private final Clock clock;

    public int process(List<MatchClaim> claims) {
        List<MatchClaim> completedClaims = matchFoundService.processBatch(claims);
        long matchedAt = clock.millis();
        for (MatchClaim claim : completedClaims) {
            recordCompletedClaim(claim, matchedAt);
        }
        return completedClaims.size();
    }

    private void recordCompletedClaim(MatchClaim claim, long matchedAt) {
        MatchTicket userA = claim.first();
        MatchTicket userB = claim.second();
        matchEngineMetrics.incrementPairs();
        recordMatchedUserWait(userA, matchedAt);
        recordMatchedUserWait(userB, matchedAt);
        log.debug("[MatchJobConsumer] FIFO 매칭 성사: matchId={}, User {} <-> User {}",
                claim.claimId(), userA.userId(), userB.userId());
    }

    private void recordMatchedUserWait(MatchTicket user, long now) {
        matchEngineMetrics.recordMatchedUserWait(Math.max(0L, now - user.entryTime()));
    }
}
