package com.sang.leagueofstar.user.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.user.controller.response.UserRankResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserRankServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime RANK_UPDATED_AT = LocalDateTime.of(2026, 6, 12, 11, 0);

    @InjectMocks
    private UserRankService userRankService;

    @Mock
    private UserReadService userReadService;

    @Mock
    private RankReadService rankReadService;

    @Test
    @DisplayName("getMyRank - core RankReadService가 반환한 UserRankInfo를 rank 응답으로 변환한다")
    void getMyRank_ReturnRank() {
        // given
        given(userReadService.findById(USER_ID)).willReturn(user());
        given(rankReadService.getUserRankInfo(USER_ID)).willReturn(rankInfo(Rank.of(Tier.GOLD, Division.IV)));

        // when
        UserRankResponse response = userRankService.getMyRank(USER_ID);

        // then
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.tier()).isEqualTo(Tier.GOLD);
        assertThat(response.division()).isEqualTo(Division.IV);
        assertThat(response.rank()).isEqualTo("GOLD_IV");
        assertThat(response.lp()).isEqualTo(40);
        assertThat(response.tierScore()).isEqualTo(13);
        assertThat(response.wins()).isEqualTo(12);
        assertThat(response.losses()).isEqualTo(8);
        assertThat(response.draws()).isEqualTo(1);
        assertThat(response.rankUpdatedAt()).isEqualTo(RANK_UPDATED_AT);
    }

    @Test
    @DisplayName("getMyRank - division이 없는 Apex rank는 tier만 rank 문자열로 반환한다")
    void getMyRank_ApexRankName() {
        // given
        given(userReadService.findById(USER_ID)).willReturn(user());
        given(rankReadService.getUserRankInfo(USER_ID)).willReturn(rankInfo(Rank.of(Tier.MASTER, null)));

        // when
        UserRankResponse response = userRankService.getMyRank(USER_ID);

        // then
        assertThat(response.tier()).isEqualTo(Tier.MASTER);
        assertThat(response.division()).isNull();
        assertThat(response.rank()).isEqualTo("MASTER");
    }

    @Test
    @DisplayName("getMyRank - core UserReadService의 USER_NOT_FOUND 예외를 그대로 전달하고 rank 조회를 하지 않는다")
    void getMyRank_PropagateUserNotFound() {
        // given
        given(userReadService.findById(USER_ID)).willThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> userRankService.getMyRank(USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.USER_NOT_FOUND));

        then(rankReadService).should(never()).getUserRankInfo(USER_ID);
    }

    @Test
    @DisplayName("getMyRank - core RankReadService의 RANK_NOT_FOUND 예외를 그대로 전달한다")
    void getMyRank_PropagateRankNotFound() {
        // given
        given(userReadService.findById(USER_ID)).willReturn(user());
        given(rankReadService.getUserRankInfo(USER_ID)).willThrow(new CoreException(CoreErrorCode.RANK_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> userRankService.getMyRank(USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.RANK_NOT_FOUND));
    }

    private User user() {
        return User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .nickname("테스터")
                .build();
    }

    private UserRankInfo rankInfo(Rank rank) {
        UserRankInfo rankInfo = UserRankInfo.builder()
                .userId(USER_ID)
                .rank(rank)
                .lp(40)
                .totalWins(12)
                .totalLosses(8)
                .totalDraws(1)
                .build();
        ReflectionTestUtils.setField(rankInfo, "updatedAt", RANK_UPDATED_AT);
        return rankInfo;
    }
}
