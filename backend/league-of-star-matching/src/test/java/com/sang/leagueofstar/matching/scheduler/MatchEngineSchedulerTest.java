package com.sang.leagueofstar.matching.scheduler;

import com.sang.leagueofstar.matching.domain.service.MatchPairingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MatchEngineSchedulerTest {

    @Test
    @DisplayName("전역 분산 락 없이 FIFO 매칭 서비스를 실행한다")
    void processMatching() {
        // given
        MatchPairingService pairingService = mock(MatchPairingService.class);
        MatchEngineScheduler scheduler = new MatchEngineScheduler(pairingService);

        // when
        scheduler.processMatching();

        // then
        then(pairingService).should().processMatching();
    }
}
