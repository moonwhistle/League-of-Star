package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.metrics.MatchResponseMetrics;
import com.sang.leagueofstar.matching.repository.MatchTimeoutStore;
import com.sang.leagueofstar.matching.domain.result.MatchResponseTimeoutResult;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchResponseTimeoutServiceTest {

    @InjectMocks
    private MatchResponseTimeoutService timeoutService;

    @Mock
    private MatchTimeoutStore timeoutStore;

    @Mock
    private MatchResponseResultService matchResponseProcessor;

    @Mock
    private MatchResponseMetrics matchResponseMetrics;

    @Mock
    private Timer.Sample sample;

    @Mock
    private Clock clock;

    @Test
    @DisplayName("due pending matchId를 claim한 뒤 timeout 정산하고 ack 처리한다")
    void processClaimedTimeout() {
        // given
        when(matchResponseMetrics.startTimer()).thenReturn(sample);
        when(clock.millis()).thenReturn(10_000L);
        when(timeoutStore.findExpiredProcessing(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of());
        when(timeoutStore.findDuePending(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of("match-1"));
        when(timeoutStore.deadlineOfPending("match-1")).thenReturn(java.util.OptionalLong.of(9_000L));
        when(timeoutStore.claim("match-1", 10_000L, 15_000L)).thenReturn(true);
        when(matchResponseProcessor.timeoutWithLock("match-1"))
                .thenReturn(MatchResponseTimeoutResult.settled(1));

        // when
        timeoutService.processTimeouts();

        // then
        verify(matchResponseProcessor).timeoutWithLock("match-1");
        verify(timeoutStore).ack("match-1");
        verify(matchResponseMetrics).recordTimeoutBatchDuration(sample);
        verify(matchResponseMetrics).recordTimeoutProcessingDelay(1_000L);
    }

    @Test
    @DisplayName("claim에 실패한 matchId는 timeout 정산하지 않는다")
    void skipUnclaimedTimeout() {
        // given
        when(matchResponseMetrics.startTimer()).thenReturn(sample);
        when(clock.millis()).thenReturn(10_000L);
        when(timeoutStore.findExpiredProcessing(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of());
        when(timeoutStore.findDuePending(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of("match-1"));
        when(timeoutStore.deadlineOfPending("match-1")).thenReturn(java.util.OptionalLong.of(10_000L));
        when(timeoutStore.claim("match-1", 10_000L, 15_000L)).thenReturn(false);

        // when
        timeoutService.processTimeouts();

        // then
        verify(matchResponseProcessor, never()).timeoutWithLock("match-1");
        verify(timeoutStore, never()).ack("match-1");
    }

    @Test
    @DisplayName("timeout 정산이 실패하면 ack하지 않아 lease 만료 후 재처리 가능하게 둔다")
    void skipAckWhenTimeoutProcessingFails() {
        // given
        when(matchResponseMetrics.startTimer()).thenReturn(sample);
        when(clock.millis()).thenReturn(10_000L);
        when(timeoutStore.findExpiredProcessing(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of());
        when(timeoutStore.findDuePending(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of("match-1"));
        when(timeoutStore.deadlineOfPending("match-1")).thenReturn(java.util.OptionalLong.of(10_000L));
        when(timeoutStore.claim("match-1", 10_000L, 15_000L)).thenReturn(true);
        doThrow(new IllegalStateException("timeout failed"))
                .when(matchResponseProcessor).timeoutWithLock("match-1");

        // when
        timeoutService.processTimeouts();

        // then
        verify(timeoutStore, never()).ack("match-1");
    }

    @Test
    @DisplayName("processing lease가 만료된 matchId는 pending으로 reclaim한다")
    void reclaimExpiredProcessing() {
        // given
        when(matchResponseMetrics.startTimer()).thenReturn(sample);
        when(clock.millis()).thenReturn(10_000L);
        when(timeoutStore.findExpiredProcessing(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of("match-1", "match-2"));
        when(timeoutStore.findDuePending(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of());

        // when
        timeoutService.processTimeouts();

        // then
        verify(timeoutStore).reclaim("match-1", 10_000L, 10_000L);
        verify(timeoutStore).reclaim("match-2", 10_000L, 10_000L);
    }

    @Test
    @DisplayName("reclaim 실패는 같은 tick의 due pending 처리를 막지 않는다")
    void continueDueProcessingWhenReclaimFails() {
        // given
        when(matchResponseMetrics.startTimer()).thenReturn(sample);
        when(clock.millis()).thenReturn(10_000L);
        when(timeoutStore.findExpiredProcessing(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of("match-expired"));
        when(timeoutStore.findDuePending(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of("match-due"));
        doThrow(new IllegalStateException("reclaim failed"))
                .when(timeoutStore).reclaim("match-expired", 10_000L, 10_000L);
        when(timeoutStore.claim("match-due", 10_000L, 15_000L)).thenReturn(true);
        when(timeoutStore.deadlineOfPending("match-due")).thenReturn(java.util.OptionalLong.of(10_000L));
        when(matchResponseProcessor.timeoutWithLock("match-due"))
                .thenReturn(MatchResponseTimeoutResult.noOp());

        // when
        timeoutService.processTimeouts();

        // then
        verify(timeoutStore).reclaim("match-expired", 10_000L, 10_000L);
        verify(matchResponseProcessor).timeoutWithLock("match-due");
        verify(timeoutStore).ack("match-due");
    }

    @Test
    @DisplayName("개별 matchId 처리 실패는 같은 batch의 나머지 처리를 막지 않는다")
    void continueWhenSingleMatchProcessingFails() {
        // given
        when(matchResponseMetrics.startTimer()).thenReturn(sample);
        when(clock.millis()).thenReturn(10_000L);
        when(timeoutStore.findExpiredProcessing(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of());
        when(timeoutStore.findDuePending(10_000L, MatchingConstants.TIMEOUT_CANDIDATE_BATCH_SIZE))
                .thenReturn(List.of("match-1", "match-2"));
        when(timeoutStore.deadlineOfPending("match-1")).thenReturn(java.util.OptionalLong.of(10_000L));
        when(timeoutStore.deadlineOfPending("match-2")).thenReturn(java.util.OptionalLong.of(10_000L));
        when(timeoutStore.claim("match-1", 10_000L, 15_000L)).thenReturn(true);
        when(timeoutStore.claim("match-2", 10_000L, 15_000L)).thenReturn(true);
        doThrow(new IllegalStateException("timeout failed"))
                .when(matchResponseProcessor).timeoutWithLock("match-1");
        when(matchResponseProcessor.timeoutWithLock("match-2"))
                .thenReturn(MatchResponseTimeoutResult.settled(0));

        // when
        timeoutService.processTimeouts();

        // then
        verify(matchResponseProcessor).timeoutWithLock("match-1");
        verify(matchResponseProcessor).timeoutWithLock("match-2");
        verify(timeoutStore, never()).ack("match-1");
        verify(timeoutStore).ack("match-2");
    }
}
