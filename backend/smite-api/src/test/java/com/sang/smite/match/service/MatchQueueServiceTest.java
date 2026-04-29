package com.sang.smite.match.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.service.RankReadService;
import com.sang.smite.matching.service.MatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchQueueServiceTest {

    @Mock
    private MatchService matchService;

    @Mock
    private RankReadService rankReadService;

    @InjectMocks
    private MatchQueueService matchQueueService;

    @Test
    @DisplayName("유저 랭크를 조회한 뒤 정상적으로 대기열에 진입한다.")
    void joinQueue_success() {
        // given
        Long userId = 1L;
        int tierScore = 15;
        UserRankInfo rankInfo = mock(UserRankInfo.class);
        given(rankInfo.getTierScore()).willReturn(tierScore);
        given(rankReadService.getUserRankInfo(userId)).willReturn(rankInfo);

        // when
        matchQueueService.joinQueue(userId);

        // then
        verify(rankReadService).getUserRankInfo(userId);
        verify(matchService).joinQueue(userId, tierScore);
    }

    @Test
    @DisplayName("유저 랭크 정보가 없으면 대기열 진입에 실패한다.")
    void joinQueue_rank_not_found() {
        // given
        Long userId = 1L;
        given(rankReadService.getUserRankInfo(userId)).willThrow(new CoreException(CoreErrorCode.RANK_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> matchQueueService.joinQueue(userId))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", CoreErrorCode.RANK_NOT_FOUND);
    }

    @Test
    @DisplayName("유저 랭크를 조회한 뒤 정상적으로 대기열에서 나간다.")
    void leaveQueue_success() {
        // given
        Long userId = 1L;
        int tierScore = 15;
        UserRankInfo rankInfo = mock(UserRankInfo.class);
        given(rankInfo.getTierScore()).willReturn(tierScore);
        given(rankReadService.getUserRankInfo(userId)).willReturn(rankInfo);

        // when
        matchQueueService.leaveQueue(userId);

        // then
        verify(rankReadService).getUserRankInfo(userId);
        verify(matchService).leaveQueue(userId, tierScore);
    }
}
