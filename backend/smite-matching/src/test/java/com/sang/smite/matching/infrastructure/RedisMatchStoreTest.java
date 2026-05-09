package com.sang.smite.matching.infrastructure;

import com.sang.smite.domain.match.domain.MatchTicket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.sang.smite.redis.AbstractRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedisMatchStoreTest extends AbstractRedisTest {

    @Autowired
    private RedisMatchStore matchStore;

    @Autowired
    private RedissonClient redissonClient;

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("대기열에 티켓을 추가하고 전체 조회할 수 있다")
    void addAndFindAll() {
        // given
        MatchTicket ticket1 = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket ticket2 = new MatchTicket(2L, 11, System.currentTimeMillis() + 100);

        // when
        matchStore.add(ticket1);
        matchStore.add(ticket2);

        // then
        List<MatchTicket> all = matchStore.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(MatchTicket::userId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("루아 스크립트를 사용하여 두 명의 유저를 원자적으로 제거할 수 있다")
    void atomicPairRemove() {
        // given
        MatchTicket userA = new MatchTicket(101L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(102L, 12, System.currentTimeMillis());
        matchStore.add(userA);
        matchStore.add(userB);

        // when
        boolean result = matchStore.atomicPairRemove(101L, 10, 102L, 12);

        // then
        assertThat(result).isTrue();
        assertThat(matchStore.findAll()).isEmpty();
    }

    @Test
    @DisplayName("한 명이라도 존재하지 않으면 루아 스크립트 삭제가 실패하고 아무도 삭제되지 않는다")
    void atomicPairRemoveFail() {
        // given
        MatchTicket userA = new MatchTicket(101L, 10, System.currentTimeMillis());
        matchStore.add(userA);

        // when
        boolean result = matchStore.atomicPairRemove(101L, 10, 999L, 12);

        // then
        assertThat(result).isFalse();
        assertThat(matchStore.findAll())
                .hasSize(1)
                .extracting(MatchTicket::userId)
                .containsExactly(101L);
    }

    @Test
    @DisplayName("countByTierScore는 해당 티어의 대기 인원만 정확히 반환한다")
    void countByTierScore() {
        // given
        MatchTicket ticket1 = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket ticket2 = new MatchTicket(2L, 10, System.currentTimeMillis() + 100);
        MatchTicket ticket3 = new MatchTicket(3L, 12, System.currentTimeMillis() + 200);

        matchStore.add(ticket1);
        matchStore.add(ticket2);
        matchStore.add(ticket3);

        // when
        int countTier10 = matchStore.countByTierScore(10);
        int countTier12 = matchStore.countByTierScore(12);
        int countTier15 = matchStore.countByTierScore(15);

        // then
        assertThat(countTier10).isEqualTo(2);
        assertThat(countTier12).isEqualTo(1);
        assertThat(countTier15).isEqualTo(0);
    }

    @Test
    @DisplayName("기존 entryTime으로 티켓을 재삽입하면 대기열 score가 유지된다")
    void reAddWithOriginalEntryTime() {
        // given
        long originalEntryTime = 100_000L;
        MatchTicket ticket = new MatchTicket(1L, 10, originalEntryTime);

        // when
        matchStore.add(ticket);

        // then
        List<MatchTicket> tickets = matchStore.findAll();
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).userId()).isEqualTo(1L);
        assertThat(tickets.get(0).tierScore()).isEqualTo(10);
        assertThat(tickets.get(0).entryTime()).isEqualTo(originalEntryTime);
        assertThat(matchStore.countByTierScore(10)).isEqualTo(1);
    }
}
