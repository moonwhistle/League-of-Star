package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.metrics.MatchEngineMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class MatchJobProcessorTest {

    private final MatchFoundService matchFoundService = mock(MatchFoundService.class);
    private final MatchEngineMetrics metrics = mock(MatchEngineMetrics.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC);
    private final MatchJobProcessor processor = new MatchJobProcessor(matchFoundService, metrics, clock);

    @Test
    @DisplayName("ACK까지 완료된 작업만 성사 건수와 대기 시간을 기록한다")
    void recordsOnlyCompletedJobs() {
        // given
        MatchClaim completed = new MatchClaim(
                "1-0",
                new MatchTicket(1L, 1_000L),
                new MatchTicket(2L, 2_000L)
        );
        MatchClaim pending = new MatchClaim(
                "2-0",
                new MatchTicket(3L, 3_000L),
                new MatchTicket(4L, 4_000L)
        );
        given(matchFoundService.processBatch(List.of(completed, pending))).willReturn(List.of(completed));

        // when
        int result = processor.process(List.of(completed, pending));

        // then
        assertThat(result).isEqualTo(1);
        then(metrics).should().incrementPairs();
        then(metrics).should().recordMatchedUserWait(4_000L);
        then(metrics).should().recordMatchedUserWait(3_000L);
        then(metrics).should(times(2)).recordMatchedUserWait(org.mockito.ArgumentMatchers.anyLong());
    }
}
