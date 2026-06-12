package com.sang.leagueofstar.domain.rank.repository;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRankInfoRepositoryTest {

    private static final int RANKER_COUNT = 5;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRankInfoRepository userRankInfoRepository;

    @Test
    @DisplayName("랭킹 row마다 user를 단건 조회하면 1 + N 조회가 발생한다")
    void rowByRowUserLookup_CausesApplicationLevelNPlusOne() {
        // given
        IntStream.rangeClosed(1, RANKER_COUNT)
                .mapToObj(this::saveRanker)
                .forEach(userRankInfoRepository::save);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // when
        List<UserRankInfo> ranks = userRankInfoRepository.findAll(rankingSort());

        ranks.forEach(rankInfo -> userRepository.findById(rankInfo.getUserId()).orElseThrow());

        // then
        assertThat(ranks).hasSize(RANKER_COUNT);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + RANKER_COUNT);
    }

    @Test
    @DisplayName("랭킹 userId를 모아 batch 조회하면 1 + 1 조회로 고정된다")
    void batchUserLookup_UsesSingleInQuery() {
        // given
        IntStream.rangeClosed(1, RANKER_COUNT)
                .mapToObj(this::saveRanker)
                .forEach(userRankInfoRepository::save);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // when
        List<UserRankInfo> ranks = userRankInfoRepository.findAll(rankingSort());
        List<Long> userIds = ranks.stream()
                .map(UserRankInfo::getUserId)
                .distinct()
                .toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // then
        assertThat(ranks).hasSize(RANKER_COUNT);
        assertThat(usersById).hasSize(RANKER_COUNT);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("top ranking은 tierScore, LP, 전적, userId 순서로 정렬된다")
    void findTopRankings_ShouldUseRankingPolicyOrder() {
        // given
        UserRankInfo platinum = saveRanker("platinum", Rank.of(Tier.PLATINUM, Division.IV), 0, 1, 5, 0);
        UserRankInfo goldWithDraw = saveRanker("gold-draw", Rank.of(Tier.GOLD, Division.IV), 50, 5, 0, 1);
        UserRankInfo goldLowLoss = saveRanker("gold-low-loss", Rank.of(Tier.GOLD, Division.IV), 50, 5, 0, 0);
        UserRankInfo goldHighLoss = saveRanker("gold-high-loss", Rank.of(Tier.GOLD, Division.IV), 50, 5, 1, 0);
        UserRankInfo silver = saveRanker("silver", Rank.of(Tier.SILVER, Division.I), 999, 999, 0, 0);
        userRankInfoRepository.saveAll(List.of(platinum, goldWithDraw, goldLowLoss, goldHighLoss, silver));
        entityManager.flush();
        entityManager.clear();

        // when
        List<UserRankInfo> rankings = userRankInfoRepository.findTopRankings(PageRequest.of(0, 5));

        // then
        assertThat(rankUserIds(rankings)).containsExactly(
                platinum.getUserId(),
                goldWithDraw.getUserId(),
                goldLowLoss.getUserId(),
                goldHighLoss.getUserId(),
                silver.getUserId()
        );
    }

    @Test
    @DisplayName("current user rank position 계산을 위해 앞선 랭커 수를 정렬 정책과 동일하게 계산한다")
    void countRankersAheadOf_ShouldUseSameRankingPolicy() {
        // given
        UserRankInfo platinum = saveRanker("position-platinum", Rank.of(Tier.PLATINUM, Division.IV), 0, 1, 5, 0);
        UserRankInfo goldWithDraw = saveRanker("position-gold-draw", Rank.of(Tier.GOLD, Division.IV), 50, 5, 0, 1);
        UserRankInfo current = saveRanker("position-current", Rank.of(Tier.GOLD, Division.IV), 50, 5, 0, 0);
        UserRankInfo behind = saveRanker("position-behind", Rank.of(Tier.GOLD, Division.IV), 50, 5, 1, 0);
        userRankInfoRepository.saveAll(List.of(platinum, goldWithDraw, current, behind));
        entityManager.flush();
        entityManager.clear();

        // when
        long aheadCount = userRankInfoRepository.countRankersAheadOf(
                current.getTierScore(),
                current.getLp(),
                current.getTotalWins(),
                current.getTotalLosses(),
                current.getTotalDraws(),
                current.getUserId()
        );

        // then
        assertThat(aheadCount).isEqualTo(2);
    }

    @Test
    @DisplayName("rank 변경 시 tierScore 저장 컬럼도 rank 값과 동기화된다")
    void updateRankAndLp_ShouldSyncTierScoreColumn() {
        // given
        UserRankInfo rankInfo = userRankInfoRepository.save(
                saveRanker("sync-tier-score", Rank.of(Tier.IRON, Division.IV), 0, 0, 0, 0)
        );
        entityManager.flush();
        entityManager.clear();

        // when
        UserRankInfo savedRankInfo = userRankInfoRepository.findById(rankInfo.getId()).orElseThrow();
        Rank nextRank = Rank.of(Tier.GOLD, Division.I);
        savedRankInfo.updateRankAndLp(nextRank, 10);
        entityManager.flush();
        entityManager.clear();

        // then
        Object tierScore = entityManager.createNativeQuery("""
                        select tier_score
                        from user_rank_info
                        where id = :id
                        """)
                .setParameter("id", rankInfo.getId())
                .getSingleResult();
        assertThat(((Number) tierScore).intValue()).isEqualTo(nextRank.getTierScore());
    }

    private UserRankInfo saveRanker(int index) {
        User user = userRepository.save(User.builder()
                .email("ranker" + index + "@example.com")
                .nickname("랭커" + index)
                .build());

        return UserRankInfo.builder()
                .userId(user.getId())
                .rank(Rank.of(Tier.GOLD, Division.IV))
                .lp(100 - index)
                .tierScore(Rank.of(Tier.GOLD, Division.IV).getTierScore())
                .totalWins(10 + index)
                .totalLosses(index)
                .totalDraws(0)
                .build();
    }

    private UserRankInfo saveRanker(String key, Rank rank, int lp, int wins, int losses, int draws) {
        User user = userRepository.save(User.builder()
                .email(key + "@example.com")
                .nickname("n" + Math.abs(key.hashCode()))
                .build());

        return UserRankInfo.builder()
                .userId(user.getId())
                .rank(rank)
                .lp(lp)
                .tierScore(rank.getTierScore())
                .totalWins(wins)
                .totalLosses(losses)
                .totalDraws(draws)
                .build();
    }

    private List<Long> rankUserIds(List<UserRankInfo> rankings) {
        return rankings.stream()
                .map(UserRankInfo::getUserId)
                .toList();
    }

    private Sort rankingSort() {
        return Sort.by(
                Sort.Order.desc("tierScore"),
                Sort.Order.desc("lp"),
                Sort.Order.desc("totalWins"),
                Sort.Order.asc("totalLosses"),
                Sort.Order.desc("totalDraws"),
                Sort.Order.asc("userId")
        );
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
