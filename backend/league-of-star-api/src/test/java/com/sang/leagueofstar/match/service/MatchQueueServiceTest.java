package com.sang.leagueofstar.match.service;

import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.matching.command.MatchQueueCommandService;
import com.sang.leagueofstar.matching.common.exception.MatchingErrorCode;
import com.sang.leagueofstar.matching.common.exception.MatchingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MatchQueueServiceTest {

    @Mock
    private MatchQueueCommandService matchService;

    @Mock
    private GameRoomReadService gameRoomReadService;

    @InjectMocks
    private MatchQueueService matchQueueService;

    @Test
    @DisplayName("진행 중인 게임룸이 없으면 FIFO 대기열에 진입한다.")
    void joinQueue_success() {
        // given
        Long userId = 1L;
        given(gameRoomReadService.existsActiveGameRoomByUserId(userId)).willReturn(false);

        // when
        matchQueueService.joinQueue(userId);

        // then
        then(gameRoomReadService).should().existsActiveGameRoomByUserId(userId);
        then(matchService).should().joinQueue(userId);
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
        then(matchService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("현재 랭크와 관계없이 FIFO 대기열에서 나간다.")
    void leaveQueue_success() {
        // given
        Long userId = 1L;

        // when
        matchQueueService.leaveQueue(userId);

        // then
        then(gameRoomReadService).shouldHaveNoInteractions();
        then(matchService).should().leaveQueue(userId);
    }
}
