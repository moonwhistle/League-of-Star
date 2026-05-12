package com.sang.smite.matching.infrastructure;

import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.redis.AbstractRedisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisMatchTimeoutStoreTest extends AbstractRedisTest {

    @Autowired
    private RedisMatchTimeoutStore timeoutStore;

    @Autowired
    private RedissonClient redissonClient;

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("pending ZSET에 timeout 대상 matchId를 deadline 기준으로 저장하고 조회할 수 있다")
    void addPendingAndFindDuePending() {
        // given
        timeoutStore.addPending("match-due-1", 1_000L);
        timeoutStore.addPending("match-due-2", 2_000L);
        timeoutStore.addPending("match-future", 3_000L);

        // when
        List<String> dueMatchIds = timeoutStore.findDuePending(2_000L, 10);

        // then
        assertThat(dueMatchIds).containsExactly("match-due-1", "match-due-2");
        assertThat(timeoutStore.pendingSize()).isEqualTo(3);
        assertThat(timeoutStore.overduePendingSize(2_000L)).isEqualTo(2);
        assertThat(timeoutStore.deadlineOfPending("match-due-1")).hasValue(1_000L);
    }

    @Test
    @DisplayName("batch size만큼 due pending matchId를 조회한다")
    void findDuePendingWithBatchSize() {
        // given
        timeoutStore.addPending("match-1", 1_000L);
        timeoutStore.addPending("match-2", 2_000L);
        timeoutStore.addPending("match-3", 3_000L);

        // when
        List<String> dueMatchIds = timeoutStore.findDuePending(3_000L, 2);

        // then
        assertThat(dueMatchIds).containsExactly("match-1", "match-2");
    }

    @Test
    @DisplayName("Lua claim은 due pending matchId를 processing으로 원자 이동한다")
    void claimDuePending() {
        // given
        timeoutStore.addPending("match-claim", 1_000L);

        // when
        boolean claimed = timeoutStore.claim("match-claim", 1_000L, 6_000L);

        // then
        assertThat(claimed).isTrue();
        assertThat(pendingSet().contains("match-claim")).isFalse();
        assertThat(processingSet().getScore("match-claim")).isEqualTo(6_000D);
        assertThat(timeoutStore.processingSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("Lua claim은 같은 matchId에 대해 한 번만 성공한다")
    void claimOnlyOnce() {
        // given
        timeoutStore.addPending("match-once", 1_000L);

        // when
        boolean firstClaim = timeoutStore.claim("match-once", 1_000L, 6_000L);
        boolean secondClaim = timeoutStore.claim("match-once", 1_000L, 6_000L);

        // then
        assertThat(firstClaim).isTrue();
        assertThat(secondClaim).isFalse();
        assertThat(processingSet().contains("match-once")).isTrue();
    }

    @Test
    @DisplayName("Lua claim은 아직 deadline이 지나지 않은 pending matchId를 가져가지 않는다")
    void claimFuturePending() {
        // given
        timeoutStore.addPending("match-future", 5_000L);

        // when
        boolean claimed = timeoutStore.claim("match-future", 4_999L, 9_999L);

        // then
        assertThat(claimed).isFalse();
        assertThat(pendingSet().contains("match-future")).isTrue();
        assertThat(processingSet().contains("match-future")).isFalse();
    }

    @Test
    @DisplayName("ack는 processing ZSET에서 처리 완료된 matchId를 제거한다")
    void ack() {
        // given
        timeoutStore.addPending("match-ack", 1_000L);
        timeoutStore.claim("match-ack", 1_000L, 6_000L);

        // when
        timeoutStore.ack("match-ack");

        // then
        assertThat(processingSet().contains("match-ack")).isFalse();
    }

    @Test
    @DisplayName("cleanup은 pending과 processing 양쪽 timeout index를 제거한다")
    void cleanup() {
        // given
        timeoutStore.addPending("match-pending", 1_000L);
        timeoutStore.addPending("match-processing", 1_000L);
        timeoutStore.claim("match-processing", 1_000L, 6_000L);

        // when
        timeoutStore.cleanup("match-pending");
        timeoutStore.cleanup("match-processing");

        // then
        assertThat(pendingSet().contains("match-pending")).isFalse();
        assertThat(processingSet().contains("match-processing")).isFalse();
    }

    @Test
    @DisplayName("만료된 processing matchId를 조회하고 Lua reclaim으로 pending에 복구할 수 있다")
    void reclaimExpiredProcessing() {
        // given
        timeoutStore.addPending("match-reclaim", 1_000L);
        timeoutStore.claim("match-reclaim", 1_000L, 6_000L);

        // when
        List<String> expiredMatchIds = timeoutStore.findExpiredProcessing(6_000L, 10);
        boolean reclaimed = timeoutStore.reclaim("match-reclaim", 6_000L, 6_000L);

        // then
        assertThat(expiredMatchIds).containsExactly("match-reclaim");
        assertThat(reclaimed).isTrue();
        assertThat(processingSet().contains("match-reclaim")).isFalse();
        assertThat(pendingSet().getScore("match-reclaim")).isEqualTo(6_000D);
    }

    @Test
    @DisplayName("Lua reclaim은 lease가 만료되지 않은 processing matchId를 복구하지 않는다")
    void reclaimNotExpiredProcessing() {
        // given
        timeoutStore.addPending("match-processing", 1_000L);
        timeoutStore.claim("match-processing", 1_000L, 6_000L);

        // when
        boolean reclaimed = timeoutStore.reclaim("match-processing", 5_999L, 5_999L);

        // then
        assertThat(reclaimed).isFalse();
        assertThat(processingSet().contains("match-processing")).isTrue();
        assertThat(pendingSet().contains("match-processing")).isFalse();
    }

    private RScoredSortedSet<String> pendingSet() {
        return redissonClient.getScoredSortedSet(MatchingConstants.TIMEOUT_PENDING_KEY, StringCodec.INSTANCE);
    }

    private RScoredSortedSet<String> processingSet() {
        return redissonClient.getScoredSortedSet(MatchingConstants.TIMEOUT_PROCESSING_KEY, StringCodec.INSTANCE);
    }
}
