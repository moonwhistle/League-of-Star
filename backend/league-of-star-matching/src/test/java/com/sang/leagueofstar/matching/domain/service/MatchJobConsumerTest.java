package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MatchJobConsumerTest {

    private final MatchJobStore matchJobStore = mock(MatchJobStore.class);
    private final MatchJobProcessor processor = mock(MatchJobProcessor.class);
    private final MatchConsumerIdentity identity = new MatchConsumerIdentity("api-1");
    private final MatchJobConsumer consumer = new MatchJobConsumer(matchJobStore, processor, identity);

    @AfterEach
    void tearDown() {
        consumer.stop();
    }

    @Test
    @DisplayName("worker 시작 시 그룹을 준비하고 blocking read로 받은 작업을 처리한다")
    void consumesNewJobs() throws Exception {
        // given
        MatchClaim job = new MatchClaim(
                "1-0",
                new MatchTicket(1L, 1_000L),
                new MatchTicket(2L, 2_000L)
        );
        CountDownLatch processed = new CountDownLatch(1);
        AtomicInteger readCount = new AtomicInteger();
        given(matchJobStore.readNew(
                identity.consumerName(),
                MatchingConstants.MATCH_JOB_CONSUMER_BATCH_SIZE,
                Duration.ofMillis(MatchingConstants.MATCH_JOB_CONSUMER_BLOCK_MILLIS)
        )).willAnswer(invocation -> {
            if (readCount.getAndIncrement() == 0) {
                return List.of(job);
            }
            Thread.sleep(1_000L);
            return List.of();
        });
        given(processor.process(List.of(job))).willAnswer(invocation -> {
            processed.countDown();
            return 1;
        });

        // when
        consumer.start();

        // then
        assertThat(processed.await(2, TimeUnit.SECONDS)).isTrue();
        then(matchJobStore).should().initializeConsumerGroup();
        then(processor).should().process(List.of(job));
        assertThat(consumer.isRunning()).isTrue();
    }
}
