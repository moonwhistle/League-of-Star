package com.sang.leagueofstar.ranking.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FIRST_USER_ID = 2L;
    private static final Long SECOND_USER_ID = 3L;

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private UserReadService userReadService;

    @Mock
    private RankReadService rankReadService;

    @Test
    @DisplayName("getRankings - top entries와 current user를 조립하고 nickname은 batch 조회한다")
    void getRankings_ReturnRankingResponse() {
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
        given(userReadService.findByIds(anyCollection()))
                .willReturn(List.of(user(FIRST_USER_ID, "LegendaryStar"), user(SECOND_USER_ID, "ShadowWalker"), currentUser));

        // when
        RankingResponse response = rankingService.getRankings(USER_ID, 5);

        // then
        assertThat(response.summary().myRankPosition()).isEqualTo(12);
        assertThat(response.summary().topPercent()).isEqualTo(8);
        assertThat(response.summary().totalRankers()).isEqualTo(150);
        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries().get(0).rankPosition()).isEqualTo(1);
        assertThat(response.entries().get(0).nickname()).isEqualTo("LegendaryStar");
        assertThat(response.entries().get(0).isCurrentUser()).isFalse();
        assertThat(response.entries().get(1).rankPosition()).isEqualTo(2);
        assertThat(response.entries().get(1).nickname()).isEqualTo("ShadowWalker");
        assertThat(response.currentUser().rankPosition()).isEqualTo(12);
        assertThat(response.currentUser().nickname()).isEqualTo("Me");
        assertThat(response.currentUser().isCurrentUser()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> userIdsCaptor =
                (ArgumentCaptor<Collection<Long>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Collection.class);
        then(userReadService).should(times(1)).findById(USER_ID);
        then(userReadService).should(times(1)).findByIds(userIdsCaptor.capture());
        assertThat(userIdsCaptor.getValue()).containsExactly(FIRST_USER_ID, SECOND_USER_ID, USER_ID);
        then(userReadService).should(never()).findById(FIRST_USER_ID);
        then(userReadService).should(never()).findById(SECOND_USER_ID);
    }

    @Test
    @DisplayName("getRankings - 현재 유저가 top entries에 있어도 currentUser row를 별도로 반환한다")
    void getRankings_CurrentUserInTopEntries() {
        // given
        User currentUser = user(USER_ID, "Me");
        UserRankInfo currentRankInfo = rankInfo(USER_ID, Rank.of(Tier.GOLD, Division.IV), 90, 20, 3, 1);

        given(userReadService.findById(USER_ID)).willReturn(currentUser);
        given(rankReadService.getUserRankInfo(USER_ID)).willReturn(currentRankInfo);
        given(rankReadService.findTopRankings(5)).willReturn(List.of(currentRankInfo));
        given(rankReadService.countRankers()).willReturn(10L);
        given(rankReadService.getRankPosition(currentRankInfo)).willReturn(1);
        given(userReadService.findByIds(anyCollection())).willReturn(List.of(currentUser));

        // when
        RankingResponse response = rankingService.getRankings(USER_ID, 5);

        // then
        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).isCurrentUser()).isTrue();
        assertThat(response.currentUser().rankPosition()).isEqualTo(1);
        assertThat(response.currentUser().isCurrentUser()).isTrue();
        assertThat(response.summary().topPercent()).isEqualTo(10);
    }

    @Test
    @DisplayName("getRankings - user가 없으면 rank 조회를 하지 않고 USER_NOT_FOUND를 전달한다")
    void getRankings_UserNotFound() {
        // given
        given(userReadService.findById(USER_ID)).willThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> rankingService.getRankings(USER_ID, 5))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.USER_NOT_FOUND));

        then(rankReadService).should(never()).getUserRankInfo(USER_ID);
    }

    @Test
    @DisplayName("getRankings - rank가 없으면 RANK_NOT_FOUND를 전달한다")
    void getRankings_RankNotFound() {
        // given
        given(userReadService.findById(USER_ID)).willReturn(user(USER_ID, "Me"));
        given(rankReadService.getUserRankInfo(USER_ID)).willThrow(new CoreException(CoreErrorCode.RANK_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> rankingService.getRankings(USER_ID, 5))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.RANK_NOT_FOUND));

        then(rankReadService).should(never()).findTopRankings(5);
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
