package com.sang.leagueofstar.matching.infrastructure.redis;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisMatchQueueStoreTest extends MatchingRedisIntegrationTest {

    @Autowired
    private RedisMatchQueueStore matchStore;
    @Autowired
    private RedisMatchJobStore matchJobStore;
    @Autowired
    private RedisMatchUserStatusStore statusStore;
    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        matchJobStore.initializeConsumerGroup();
    }

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("대기 시작 시각순으로 단일 FIFO 큐를 조회한다")
    void addAndFindAllInEntryTimeOrder() {
        // given
        matchStore.add(new MatchTicket(2L, 2_000L));
        matchStore.add(new MatchTicket(1L, 1_000L));
        matchStore.add(new MatchTicket(3L, 3_000L));

        // when
        List<MatchTicket> tickets = matchStore.findAll();

        // then
        assertThat(tickets).extracting(MatchTicket::userId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("오래 기다린 사용자부터 Stream MatchJob으로 원자 전환한다")
    void enqueuesOldestMatches() {
        // given
        addTickets(1L, 5L);

        // when
        int produced = matchStore.enqueueOldestMatches(4);
        List<MatchClaim> jobs = matchJobStore.readNew("consumer-a", 10, Duration.ZERO);

        // then
        assertThat(produced).isEqualTo(2);
        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(job -> job.first().userId()).containsExactly(1L, 3L);
        assertThat(matchStore.findAll()).containsExactly(new MatchTicket(5L, 5_000L));
        assertThat(matchJobStore.pendingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("작업 완료 시 상태 변경과 ACK를 원자 처리하고 완료 entry를 정리한다")
    void completesJobs() {
        // given
        addTickets(1L, 2L);
        matchStore.enqueueOldestMatches(2);
        MatchClaim job = matchJobStore.readNew("consumer-a", 1, Duration.ZERO).get(0);

        // when
        int completed = matchJobStore.complete(List.of(job), 60L);

        // then
        assertThat(completed).isEqualTo(1);
        assertThat(matchJobStore.pendingCount()).isZero();
        assertThat(matchJobStore.streamSize()).isZero();
        assertThat(statusStore.getStatus(1L)).contains(MatchStatus.FOUND);
        assertThat(statusStore.getStatus(2L)).contains(MatchStatus.FOUND);
    }

    @Test
    @DisplayName("유효하지 않은 ID가 섞인 batch는 상태와 ACK를 일부 반영하지 않는다")
    void rejectsEntireCompletionBatchWhenJobIsMissing() {
        // given
        addTickets(1L, 2L);
        matchStore.enqueueOldestMatches(2);
        MatchClaim valid = matchJobStore.readNew("consumer-a", 1, Duration.ZERO).get(0);
        MatchClaim missing = claim("0-1", 3L);

        // when
        int completed = matchJobStore.complete(List.of(valid, missing), 60L);

        // then
        assertThat(completed).isZero();
        assertThat(matchJobStore.pendingCount()).isEqualTo(1);
        assertThat(statusStore.getStatus(1L)).isEmpty();
        assertThat(statusStore.getStatus(2L)).isEmpty();
    }

    @Test
    @DisplayName("ACK되지 않은 작업은 XAUTOCLAIM으로 다른 consumer가 인계한다")
    void autoClaimsPendingJob() {
        // given
        addTickets(1L, 2L);
        matchStore.enqueueOldestMatches(2);
        MatchClaim original = matchJobStore.readNew("consumer-a", 1, Duration.ZERO).get(0);

        // when
        List<MatchClaim> recovered = matchJobStore.autoClaim("consumer-b", 0L, "0-0", 1).claims();

        // then
        assertThat(recovered).containsExactly(original);
        assertThat(matchJobStore.pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("대기 인원이 홀수이면 마지막 사용자를 waiting 큐에 유지한다")
    void keepsLastTicketWhenQueueSizeIsOdd() {
        // given
        addTickets(1L, 3L);

        // when
        int produced = matchStore.enqueueOldestMatches(100);
        List<MatchClaim> jobs = matchJobStore.readNew("consumer-a", 10, Duration.ZERO);

        // then
        assertThat(produced).isEqualTo(1);
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).first().userId()).isEqualTo(1L);
        assertThat(matchStore.findAll()).containsExactly(new MatchTicket(3L, 3_000L));
    }

    @Test
    @DisplayName("두 Producer가 동시에 실행되어도 200명을 중복 매칭하지 않는다")
    void concurrentProducersDoNotOverlap() throws Exception {
        // given
        addTickets(1L, 200L);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> matchStore.enqueueOldestMatches(100),
                    () -> matchStore.enqueueOldestMatches(100)
            );

            // when
            List<Future<Integer>> futures = executor.invokeAll(tasks);
            List<MatchClaim> jobs = matchJobStore.readNew("consumer-a", 100, Duration.ZERO);
            List<Long> matchedUserIds = jobs.stream()
                    .flatMap(job -> Stream.of(job.first().userId(), job.second().userId()))
                    .toList();

            // then
            assertThat(futures).allSatisfy(future -> assertThat(resultOf(future)).isEqualTo(50));
            assertThat(matchedUserIds).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1L, 200L).boxed().toList()
            );
            assertThat(matchedUserIds).doesNotHaveDuplicates();
            assertThat(matchStore.count()).isZero();
            assertThat(matchJobStore.pendingCount()).isEqualTo(100);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("batch 최대 인원은 2 이상의 짝수만 허용한다")
    void rejectsInvalidBatchSize() {
        // when & then
        assertThatThrownBy(() -> matchStore.enqueueOldestMatches(1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matchStore.enqueueOldestMatches(3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void addTickets(long firstUserId, long lastUserId) {
        for (long userId = firstUserId; userId <= lastUserId; userId++) {
            matchStore.add(new MatchTicket(userId, userId * 1_000L));
        }
    }

    private MatchClaim claim(String claimId, long firstUserId) {
        return new MatchClaim(
                claimId,
                new MatchTicket(firstUserId, firstUserId * 1_000L),
                new MatchTicket(firstUserId + 1, (firstUserId + 1) * 1_000L)
        );
    }

    private int resultOf(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
