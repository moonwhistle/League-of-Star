package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.metrics.MatchEngineMetrics;
import com.sang.leagueofstar.matching.repository.MatchJobRecoveryBatch;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MatchClaimRecoveryServiceTest {

    private final MatchJobStore matchJobStore = mock(MatchJobStore.class);
    private final MatchJobProcessor matchJobProcessor = mock(MatchJobProcessor.class);
    private final MatchEngineMetrics metrics = mock(MatchEngineMetrics.class);
    private final MatchConsumerIdentity identity = new MatchConsumerIdentity("api-1");
    private final MatchClaimRecoveryService recoveryService = new MatchClaimRecoveryService(
            matchJobStore,
            matchJobProcessor,
            identity,
            metrics
    );

    @Test
    @DisplayName("idle 기준을 넘긴 PEL 작업을 다른 consumer로 인계해 재처리한다")
    void recoversPendingJobs() {
        // given
        MatchClaim job = claim("1-0", 1L);
        given(matchJobStore.autoClaim(
                identity.recoveryConsumerName(),
                MatchingConstants.MATCH_JOB_MIN_IDLE_MILLIS,
                "0-0",
                MatchingConstants.MATCH_CLAIM_RECOVERY_BATCH_SIZE
        )).willReturn(new MatchJobRecoveryBatch("2-0", List.of(job)));

        // when
        recoveryService.recoverExpiredClaims();

        // then
        then(metrics).should().incrementRecoveredClaims(1);
        then(matchJobProcessor).should().process(List.of(job));
    }

    @Test
    @DisplayName("인계할 작업이 없으면 처리와 복구 지표를 생략한다")
    void skipsWhenNoPendingJob() {
        // given
        given(matchJobStore.autoClaim(
                identity.recoveryConsumerName(),
                MatchingConstants.MATCH_JOB_MIN_IDLE_MILLIS,
                "0-0",
                MatchingConstants.MATCH_CLAIM_RECOVERY_BATCH_SIZE
        )).willReturn(new MatchJobRecoveryBatch("0-0", List.of()));

        // when
        recoveryService.recoverExpiredClaims();

        // then
        then(matchJobProcessor).shouldHaveNoInteractions();
        then(metrics).shouldHaveNoInteractions();
    }

    private MatchClaim claim(String id, long firstUserId) {
        return new MatchClaim(
                id,
                new MatchTicket(firstUserId, 1_000L),
                new MatchTicket(firstUserId + 1, 2_000L)
        );
    }
}
