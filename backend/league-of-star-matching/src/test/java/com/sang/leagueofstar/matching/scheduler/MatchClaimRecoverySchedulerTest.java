package com.sang.leagueofstar.matching.scheduler;

import com.sang.leagueofstar.matching.domain.service.MatchClaimRecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MatchClaimRecoverySchedulerTest {

    @Test
    @DisplayName("주기적으로 만료된 매칭 claim 복구를 요청한다")
    void recoverExpiredClaims() {
        // given
        MatchClaimRecoveryService recoveryService = mock(MatchClaimRecoveryService.class);
        MatchClaimRecoveryScheduler scheduler = new MatchClaimRecoveryScheduler(recoveryService);

        // when
        scheduler.recoverExpiredClaims();

        // then
        then(recoveryService).should().recoverExpiredClaims();
    }
}
