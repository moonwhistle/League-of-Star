package com.sang.smite.matching.domain.service;

import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.matching.metrics.MatchEngineMetrics;
import com.sang.smite.matching.repository.MatchQueueStore;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchPairingServiceTest {

    @InjectMocks
    private MatchPairingService matchEngineService;

    @Mock
    private MatchQueueStore matchStore;

    @Mock
    private MatchFoundService matchFoundService;

    @Mock
    private MatchEngineMetrics matchEngineMetrics;

    @Mock
    private Clock clock;

    @BeforeEach
    void setUp() {
        lenient().when(clock.millis()).thenReturn(System.currentTimeMillis());
    }

    @Test
    @DisplayName("대기 인원 0~1명일 때 atomicPairRemove와 후처리가 호출되지 않음")
    void testNotEnoughUsers() {
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        
        List<MatchTicket> tickets = List.of(new MatchTicket(1L, 10, System.currentTimeMillis()));
        given(matchStore.findAll()).willReturn(new ArrayList<>(tickets));

        matchEngineService.processMatching();

        verify(matchStore, never()).atomicPairRemove(anyLong(), anyInt(), anyLong(), anyInt());
        verify(matchFoundService, never()).process(any(), any());
        verify(matchEngineMetrics).recordScannedTickets(1);
        verify(matchEngineMetrics).recordPairsPerScan(0);
        verify(matchEngineMetrics).recordScanDuration(mockSample);
    }

    @Test
    @DisplayName("FIFO 정렬 후 가장 오래 기다린 유저부터 후보를 탐색함")
    void testFifoOrdering() {
        long now = System.currentTimeMillis();
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);

        MatchTicket lateUser = new MatchTicket(1L, 10, now - 1000);
        MatchTicket earlyUser = new MatchTicket(2L, 10, now - 5000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(lateUser, earlyUser)));
        given(matchStore.atomicPairRemove(2L, 10, 1L, 10)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(2L, 10, 1L, 10);
        verify(matchFoundService).process(earlyUser, lateUser);
    }

    @Test
    @DisplayName("Sliding Window: 0~10초 대기 시 ±1 티어만 매칭")
    void testSlidingWindow_Level1() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 10, now - 5000); // 5초 대기 (±1)
        MatchTicket userB = new MatchTicket(2L, 12, now - 4000); // 티어 차이 2 -> 불가
        MatchTicket userC = new MatchTicket(3L, 11, now - 3000); // 티어 차이 1 -> 매칭 가능

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        given(matchStore.atomicPairRemove(1L, 10, 3L, 11)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 10, 3L, 11);
        verify(matchFoundService).process(userA, userC);
        verify(matchStore, never()).atomicPairRemove(1L, 10, 2L, 12);
    }

    @Test
    @DisplayName("Sliding Window: 11~20초 대기 시 ±2 티어까지 매칭")
    void testSlidingWindow_Level2() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 10, now - 15000); // 15초 대기 (±2)
        MatchTicket userB = new MatchTicket(2L, 13, now - 4000); // 티어 차이 3 -> 불가
        MatchTicket userC = new MatchTicket(3L, 12, now - 3000); // 티어 차이 2 -> 매칭 가능

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        given(matchStore.atomicPairRemove(1L, 10, 3L, 12)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 10, 3L, 12);
        verify(matchFoundService).process(userA, userC);
        verify(matchStore, never()).atomicPairRemove(1L, 10, 2L, 13);
    }

    @Test
    @DisplayName("Sliding Window: 21~30초 대기 시 ±4 티어까지 매칭")
    void testSlidingWindow_Level3() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 10, now - 25000); // 25초 대기 (±4)
        MatchTicket userB = new MatchTicket(2L, 15, now - 4000); // 티어 차이 5 -> 불가
        MatchTicket userC = new MatchTicket(3L, 14, now - 3000); // 티어 차이 4 -> 매칭 가능

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        given(matchStore.atomicPairRemove(1L, 10, 3L, 14)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 10, 3L, 14);
        verify(matchFoundService).process(userA, userC);
        verify(matchStore, never()).atomicPairRemove(1L, 10, 2L, 15);
    }

    @Test
    @DisplayName("Sliding Window: 30초 정확히 대기 시 ±4 티어까지만 매칭")
    void testSlidingWindow_Level4() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 10, now - 30_000); // 30초 대기 (±4)
        MatchTicket userB = new MatchTicket(2L, 15, now - 4000); // 티어 차이 5 -> 불가
        MatchTicket userC = new MatchTicket(3L, 14, now - 3000); // 티어 차이 4 -> 매칭 가능

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        given(matchStore.atomicPairRemove(1L, 10, 3L, 14)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 10, 3L, 14);
        verify(matchFoundService).process(userA, userC);
        verify(matchStore, never()).atomicPairRemove(1L, 10, 2L, 15);
    }

    @Test
    @DisplayName("Sliding Window: 30초 초과 대기 시 전체 tierScore 범위 매칭")
    void testSlidingWindow_FullRangeAfterThirtySeconds() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 1, now - 31_000); // 31초 대기 (전체 허용)
        MatchTicket userB = new MatchTicket(2L, 37, now - 4000); // 전체 tierScore 차이 36 -> 매칭 가능

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB)));
        given(matchStore.atomicPairRemove(1L, 1, 2L, 37)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 1, 2L, 37);
        verify(matchFoundService).process(userA, userB);
    }

    @Test
    @DisplayName("Sliding Window: 30초 초과 대기 시 기존 ±8 초과 후보도 매칭")
    void testSlidingWindow_MatchesCandidateBeyondPreviousMaxAfterThirtySeconds() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 10, now - 31_000); // 31초 대기 (전체 허용)
        MatchTicket userB = new MatchTicket(2L, 19, now - 4_000); // 기존 ±8 초과 차이 9 -> 매칭 가능

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB)));
        given(matchStore.atomicPairRemove(1L, 10, 2L, 19)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 10, 2L, 19);
        verify(matchFoundService).process(userA, userB);
    }

    @Test
    @DisplayName("Placement: 배치 유저끼리는 tierScore 9 diff 0으로 즉시 매칭")
    void matchPlacementUsersImmediately() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 9, now - 5_000);
        MatchTicket userB = new MatchTicket(2L, 9, now - 4_000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB)));
        given(matchStore.atomicPairRemove(1L, 9, 2L, 9)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 9, 2L, 9);
        verify(matchFoundService).process(userA, userB);
    }

    @Test
    @DisplayName("Placement: 0~10초 대기 시 tierScore 9 기준 ±1 정책 재사용")
    void matchPlacementUserWithFirstStepRange() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 9, now - 5_000);
        MatchTicket userB = new MatchTicket(2L, 11, now - 4_000);
        MatchTicket userC = new MatchTicket(3L, 8, now - 3_000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        given(matchStore.atomicPairRemove(1L, 9, 3L, 8)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 9, 3L, 8);
        verify(matchFoundService).process(userA, userC);
        verify(matchStore, never()).atomicPairRemove(1L, 9, 2L, 11);
    }

    @Test
    @DisplayName("Placement: 10초 초과~20초 대기 시 tierScore 9 기준 ±2 정책 재사용")
    void matchPlacementUserWithSecondStepRange() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 9, now - 15_000);
        MatchTicket userB = new MatchTicket(2L, 12, now - 4_000);
        MatchTicket userC = new MatchTicket(3L, 7, now - 3_000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        given(matchStore.atomicPairRemove(1L, 9, 3L, 7)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 9, 3L, 7);
        verify(matchFoundService).process(userA, userC);
        verify(matchStore, never()).atomicPairRemove(1L, 9, 2L, 12);
    }

    @Test
    @DisplayName("Placement: 20초 초과~30초 대기 시 tierScore 9 기준 ±4 정책 재사용")
    void matchPlacementUserWithThirdStepRange() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 9, now - 25_000);
        MatchTicket userB = new MatchTicket(2L, 14, now - 4_000);
        MatchTicket userC = new MatchTicket(3L, 5, now - 3_000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        given(matchStore.atomicPairRemove(1L, 9, 3L, 5)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 9, 3L, 5);
        verify(matchFoundService).process(userA, userC);
        verify(matchStore, never()).atomicPairRemove(1L, 9, 2L, 14);
    }

    @Test
    @DisplayName("Placement: 30초 초과 대기 시 tierScore 9 기준 전체 tierScore 매칭 정책 재사용")
    void matchPlacementUserWithFullRangeAfterThirtySeconds() {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, 9, now - 31_000);
        MatchTicket userB = new MatchTicket(2L, 37, now - 4_000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB)));
        given(matchStore.atomicPairRemove(1L, 9, 2L, 37)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, 9, 2L, 37);
        verify(matchFoundService).process(userA, userB);
    }

    @ParameterizedTest(name = "{0} 동일 tierScore는 0~10초 구간에도 즉시 매칭")
    @CsvSource({
            "Master, 29",
            "Grandmaster, 33",
            "Challenger, 37"
    })
    void matchApexSameTierScoreImmediately(String tierName, int tierScore) {
        long now = 100_000L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(now);

        MatchTicket userA = new MatchTicket(1L, tierScore, now - 5_000);
        MatchTicket userB = new MatchTicket(2L, tierScore, now - 4_000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB)));
        given(matchStore.atomicPairRemove(1L, tierScore, 2L, tierScore)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchStore).atomicPairRemove(1L, tierScore, 2L, tierScore);
        verify(matchFoundService).process(userA, userB);
    }

    @Test
    @DisplayName("atomicPairRemove가 false 반환 시 후처리 패스하고 다음 후보 탐색")
    void testAtomicRemoveFailContinues() {
        long now = System.currentTimeMillis();
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);

        MatchTicket userA = new MatchTicket(1L, 10, now - 5000);
        MatchTicket userB = new MatchTicket(2L, 10, now - 4000);
        MatchTicket userC = new MatchTicket(3L, 10, now - 3000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC)));
        
        given(matchStore.atomicPairRemove(1L, 10, 2L, 10)).willReturn(false); // 실패 모의
        given(matchStore.atomicPairRemove(1L, 10, 3L, 10)).willReturn(true);  // 성공 모의

        matchEngineService.processMatching();

        verify(matchFoundService, never()).process(userA, userB);
        verify(matchFoundService).process(userA, userC);
        verify(matchEngineMetrics).incrementAtomicPairFailures();
    }

    @Test
    @DisplayName("한 스캔에서 이미 매칭된 유저는 중복 매칭되지 않음")
    void testAlreadyPairedUserIgnored() {
        long now = System.currentTimeMillis();
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);

        MatchTicket userA = new MatchTicket(1L, 10, now - 5000);
        MatchTicket userB = new MatchTicket(2L, 10, now - 4000);
        MatchTicket userC = new MatchTicket(3L, 10, now - 3000);
        MatchTicket userD = new MatchTicket(4L, 10, now - 2000);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC, userD)));
        
        given(matchStore.atomicPairRemove(1L, 10, 2L, 10)).willReturn(true);
        given(matchStore.atomicPairRemove(3L, 10, 4L, 10)).willReturn(true);

        matchEngineService.processMatching();

        // userB가 userC의 후보로 탐색되지 않아야 함
        verify(matchStore, never()).atomicPairRemove(3L, 10, 2L, 10);
        verify(matchStore).atomicPairRemove(3L, 10, 4L, 10);
        
        verify(matchEngineMetrics, times(2)).incrementPairs();
        verify(matchEngineMetrics).recordPairsPerScan(2);
    }

    @Test
    @DisplayName("매칭 대기 시간은 스캔 시작 시각이 아니라 페어별 매칭 성사 시각 기준으로 기록")
    void recordMatchedUserWaitUsesPairMatchedAt() {
        long entryTime = 100_000L;
        long scanStartedAt = entryTime + 1_000L;
        long firstPairMatchedAt = entryTime + 1_100L;
        long secondPairMatchedAt = entryTime + 4_300L;
        Timer.Sample mockSample = mock(Timer.Sample.class);
        given(matchEngineMetrics.startScanTimer()).willReturn(mockSample);
        given(clock.millis()).willReturn(scanStartedAt, firstPairMatchedAt, secondPairMatchedAt);

        MatchTicket userA = new MatchTicket(1L, 10, entryTime);
        MatchTicket userB = new MatchTicket(2L, 10, entryTime);
        MatchTicket userC = new MatchTicket(3L, 10, entryTime);
        MatchTicket userD = new MatchTicket(4L, 10, entryTime);

        given(matchStore.findAll()).willReturn(new ArrayList<>(List.of(userA, userB, userC, userD)));
        given(matchStore.atomicPairRemove(1L, 10, 2L, 10)).willReturn(true);
        given(matchStore.atomicPairRemove(3L, 10, 4L, 10)).willReturn(true);

        matchEngineService.processMatching();

        verify(matchEngineMetrics, times(2)).recordMatchedUserWait(1_100L);
        verify(matchEngineMetrics, times(2)).recordMatchedUserWait(4_300L);
        verify(matchEngineMetrics, never()).recordMatchedUserWait(1_000L);
    }
}
