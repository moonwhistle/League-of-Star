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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
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
