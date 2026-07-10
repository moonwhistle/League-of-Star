package com.sang.leagueofstar.ranking.service;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.ranking.controller.response.RankingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RankingBaselineServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FIRST_USER_ID = 2L;
    private static final Long SECOND_USER_ID = 3L;

    @InjectMocks
    private RankingBaselineService rankingBaselineService;

    @Mock
    private UserReadService userReadService;

    @Mock
    private RankReadService rankReadService;

    @Test
    @DisplayName("getRankings - baseline intentionally looks up each ranking row user one by one")
    void getRankings_UsesRowByRowUserLookup() {
        // given
        User currentUser = user(USER_ID, "Me");
        UserRankInfo currentRankInfo = rankInfo(USER_ID, Rank.of(Tier.SILVER, Division.I), 40, 10, 8, 2);
        UserRankInfo firstRankInfo = rankInfo(FIRST_USER_ID, Rank.of(Tier.GOLD, Division.IV), 90, 20, 3, 1);
        UserRankInfo secondRankInfo = rankInfo(SECOND_USER_ID, Rank.of(Tier.GOLD, Division.IV), 80, 18, 4, 0);

        given(userReadService.findById(USER_ID)).willReturn(currentUser);
        given(rankReadService.getUserRankInfo(USER_ID)).willReturn(currentRankInfo);
        given(rankReadService.findTopRankings(5)).willReturn(List.of(firstRankInfo, secondRankInfo));
        given(rankReadService.countRankers()).willReturn(150L);
        given(rankReadService.getRankPosition(currentRankInfo)).willReturn(12);
        given(userReadService.findById(FIRST_USER_ID)).willReturn(user(FIRST_USER_ID, "LegendaryStar"));
        given(userReadService.findById(SECOND_USER_ID)).willReturn(user(SECOND_USER_ID, "ShadowWalker"));

        // when
        RankingResponse response = rankingBaselineService.getRankings(USER_ID, 5);

        // then
        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries().get(0).nickname()).isEqualTo("LegendaryStar");
        assertThat(response.entries().get(1).nickname()).isEqualTo("ShadowWalker");
        assertThat(response.currentUser().nickname()).isEqualTo("Me");

        then(userReadService).should(times(1)).findById(USER_ID);
        then(userReadService).should(times(1)).findById(FIRST_USER_ID);
        then(userReadService).should(times(1)).findById(SECOND_USER_ID);
        then(userReadService).should(never()).findByIds(org.mockito.ArgumentMatchers.anyCollection());
    }

    private User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .nickname(nickname)
                .build();
    }

    private UserRankInfo rankInfo(Long userId, Rank rank, int lp, int wins, int losses, int draws) {
        return UserRankInfo.builder()
                .userId(userId)
                .rank(rank)
                .lp(lp)
                .totalWins(wins)
                .totalLosses(losses)
                .totalDraws(draws)
                .build();
    }
}
