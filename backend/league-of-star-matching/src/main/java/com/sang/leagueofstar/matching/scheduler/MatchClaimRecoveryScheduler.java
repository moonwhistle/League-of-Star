package com.sang.leagueofstar.matching.scheduler;

import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.domain.service.MatchClaimRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PEL에서 idle 기준을 넘긴 매칭 작업의 복구를 주기적으로 실행합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchClaimRecoveryScheduler {

    private final MatchClaimRecoveryService recoveryService;

    @Scheduled(fixedDelayString = MatchingConstants.MATCH_CLAIM_RECOVERY_FIXED_DELAY_MS)
    public void recoverExpiredClaims() {
        recoveryService.recoverExpiredClaims();
    }
}
