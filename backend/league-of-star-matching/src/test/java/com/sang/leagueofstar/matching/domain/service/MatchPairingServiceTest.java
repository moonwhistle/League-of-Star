package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.matching.metrics.MatchEngineMetrics;
import com.sang.leagueofstar.matching.repository.MatchQueueStore;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MatchPairingServiceTest {

    private static final int BATCH_SIZE = 100;

    @Mock
    private MatchQueueStore matchStore;
    @Mock
    private MatchEngineMetrics metrics;

    private MatchPairingService service;
    private Timer.Sample timerSample;

    @BeforeEach
    void setUp() {
        service = new MatchPairingService(matchStore, metrics);
        timerSample = org.mockito.Mockito.mock(Timer.Sample.class);
        given(metrics.startScanTimer()).willReturn(timerSample);
    }

    @Test
    @DisplayName("두 명 미만이면 MatchJob을 생성하지 않는다")
    void noJob() {
        // given
        given(matchStore.count()).willReturn(1);
        given(matchStore.enqueueOldestMatches(BATCH_SIZE)).willReturn(0);

        // when
        service.processMatching();

        // then
        then(metrics).should().recordPairsPerScan(0);
        then(metrics).should().recordScanDuration(timerSample);
    }

    @Test
    @DisplayName("부분 batch를 생성하면 현재 스캔을 종료한다")
    void stopsAfterPartialBatch() {
        // given
        given(matchStore.count()).willReturn(40);
        given(matchStore.enqueueOldestMatches(BATCH_SIZE)).willReturn(20);

        // when
        service.processMatching();

        // then
        then(matchStore).should().enqueueOldestMatches(BATCH_SIZE);
        then(metrics).should().recordPairsPerScan(20);
    }

    @Test
    @DisplayName("100명 batch가 가득 차면 다음 batch를 이어서 생성한다")
    void continuesAfterFullBatch() {
        // given
        given(matchStore.count()).willReturn(102);
        given(matchStore.enqueueOldestMatches(BATCH_SIZE)).willReturn(50, 1);

        // when
        service.processMatching();

        // then
        then(matchStore).should(times(2)).enqueueOldestMatches(BATCH_SIZE);
        then(metrics).should().recordPairsPerScan(51);
        then(metrics).should(times(2)).incrementAtomicBatchAttempts();
    }

    @Test
    @DisplayName("MatchJob 생성 예외를 실패 지표에 기록하고 전파한다")
    void recordsEnqueueFailure() {
        // given
        given(matchStore.count()).willReturn(100);
        willThrow(new RuntimeException("redis failure"))
                .given(matchStore).enqueueOldestMatches(BATCH_SIZE);

        // when & then
        assertThatThrownBy(service::processMatching)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis failure");
        then(metrics).should().incrementAtomicBatchAttempts();
        then(metrics).should().incrementAtomicBatchFailures();
        then(metrics).should().recordScanDuration(timerSample);
    }
}
