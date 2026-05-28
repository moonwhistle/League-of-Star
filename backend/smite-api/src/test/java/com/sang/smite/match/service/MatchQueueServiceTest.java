package com.sang.smite.match.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.service.RankReadService;
import com.sang.smite.matching.command.MatchQueueCommandService;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MatchQueueServiceTest {

    @Mock
    private MatchQueueCommandService matchService;

    @Mock
    private RankReadService rankReadService;

    @Mock
    private GameRoomReadService gameRoomReadService;

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
        given(gameRoomReadService.existsActiveGameRoomByUserId(userId)).willReturn(false);
        given(rankReadService.getUserRankInfo(userId)).willReturn(rankInfo);

        // when
        matchQueueService.joinQueue(userId);

        // then
        verify(gameRoomReadService).existsActiveGameRoomByUserId(userId);
        verify(rankReadService).getUserRankInfo(userId);
        verify(matchService).joinQueue(userId, tierScore);
    }

    @Test
    @DisplayName("진행 중인 게임룸이 있으면 대기열 진입에 실패한다.")
    void joinQueue_activeGameRoom_exists() {
        // given
        Long userId = 1L;
        given(gameRoomReadService.existsActiveGameRoomByUserId(userId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> matchQueueService.joinQueue(userId))
                .isInstanceOf(MatchingException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.ACTIVE_GAME_ROOM_EXISTS);
        verify(gameRoomReadService).existsActiveGameRoomByUserId(userId);
        verifyNoInteractions(rankReadService);
        verifyNoInteractions(matchService);
    }

    @Test
    @DisplayName("유저 랭크 정보가 없으면 대기열 진입에 실패한다.")
    void joinQueue_rank_not_found() {
        // given
        Long userId = 1L;
        given(gameRoomReadService.existsActiveGameRoomByUserId(userId)).willReturn(false);
        given(rankReadService.getUserRankInfo(userId)).willThrow(new CoreException(CoreErrorCode.RANK_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> matchQueueService.joinQueue(userId))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", CoreErrorCode.RANK_NOT_FOUND);
        verify(gameRoomReadService).existsActiveGameRoomByUserId(userId);
        verifyNoInteractions(matchService);
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
        verifyNoInteractions(gameRoomReadService);
        verify(rankReadService).getUserRankInfo(userId);
        verify(matchService).leaveQueue(userId, tierScore);
    }
}
