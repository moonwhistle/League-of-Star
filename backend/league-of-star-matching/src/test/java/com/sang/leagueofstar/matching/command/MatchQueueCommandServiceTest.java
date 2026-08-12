package com.sang.leagueofstar.matching.command;

import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.common.exception.MatchingErrorCode;
import com.sang.leagueofstar.matching.common.exception.MatchingException;
import com.sang.leagueofstar.matching.repository.MatchQueueStore;
import com.sang.leagueofstar.matching.repository.MatchUserStatusStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MatchQueueCommandServiceTest {

    @Mock
    private MatchQueueStore matchStore;

    @Mock
    private MatchUserStatusStore userStatusStore;

    @InjectMocks
    private MatchQueueCommandService matchService;

    @Test
    @DisplayName("정상적으로 매칭 대기열에 진입한다.")
    void joinQueue_success() {
        // given
        Long userId = 1L;
        given(userStatusStore.setStatusIfAbsent(eq(userId), eq(MatchStatus.MATCHING), any(Long.class))).willReturn(true);

        // when
        matchService.joinQueue(userId);

        // then
        then(userStatusStore).should().setStatusIfAbsent(eq(userId), eq(MatchStatus.MATCHING), any(Long.class));
        then(matchStore).should().add(any(MatchTicket.class));
    }

    @Test
    @DisplayName("이미 매칭 중인 유저가 진입 시도 시 예외가 발생한다.")
    void joinQueue_already_matching() {
        // given
        Long userId = 1L;
        given(userStatusStore.setStatusIfAbsent(eq(userId), eq(MatchStatus.MATCHING), any(Long.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> matchService.joinQueue(userId))
                .isInstanceOf(MatchingException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.ALREADY_IN_QUEUE);
        then(matchStore).should(never()).add(any(MatchTicket.class));
    }

    @Test
    @DisplayName("대기열 추가에 실패하면 매칭 상태를 롤백한다.")
    void joinQueue_queueAddFailed_removeStatus() {
        // given
        Long userId = 1L;
        given(userStatusStore.setStatusIfAbsent(eq(userId), eq(MatchStatus.MATCHING), any(Long.class))).willReturn(true);
        willThrow(new IllegalStateException("queue add failed")).given(matchStore).add(any(MatchTicket.class));

        // when & then
        assertThatThrownBy(() -> matchService.joinQueue(userId))
                .isInstanceOf(MatchingException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.MATCH_QUEUE_ADD_ERROR);
        then(userStatusStore).should().removeStatus(userId);
    }

    @Test
    @DisplayName("정상적으로 매칭 대기열에서 나간다.")
    void leaveQueue_success() {
        // given
        Long userId = 1L;
        given(userStatusStore.getStatus(userId)).willReturn(Optional.of(MatchStatus.MATCHING));
        given(matchStore.remove(userId)).willReturn(true);

        // when
        matchService.leaveQueue(userId);

        // then
        then(matchStore).should().remove(userId);
        then(userStatusStore).should().removeStatus(userId);
    }

    @Test
    @DisplayName("대기열에 없는 유저가 취소 시도 시 예외가 발생한다.")
    void leaveQueue_not_in_queue() {
        // given
        Long userId = 1L;
        given(userStatusStore.getStatus(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> matchService.leaveQueue(userId))
                .isInstanceOf(MatchingException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.NOT_IN_QUEUE);
    }

    @Test
    @DisplayName("취소 시도 중 매칭 엔진이 이미 큐에서 유저를 꺼내갔다면 예외가 발생한다.")
    void leaveQueue_already_picked_by_engine() {
        // given
        Long userId = 1L;
        given(userStatusStore.getStatus(userId)).willReturn(Optional.of(MatchStatus.MATCHING));
        given(matchStore.remove(userId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> matchService.leaveQueue(userId))
                .isInstanceOf(MatchingException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.NOT_IN_QUEUE);
    }
}
