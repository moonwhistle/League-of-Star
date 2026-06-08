package com.sang.leagueofstar.matching.command;

import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.common.exception.MatchingErrorCode;
import com.sang.leagueofstar.matching.common.exception.MatchingException;
import com.sang.leagueofstar.matching.repository.MatchQueueStore;
import com.sang.leagueofstar.matching.repository.MatchUserStatusStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FinishedGameRequeuePolicyTest {

    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;
    private static final int TIER_SCORE = 10;

    private final InMemoryMatchUserStatusStore userStatusStore = new InMemoryMatchUserStatusStore();
    private final MatchQueueStore matchQueueStore = mock(MatchQueueStore.class);
    private final MatchUserStatusCommandService matchUserStatusCommandService =
            new MatchUserStatusCommandService(userStatusStore);
    private final MatchQueueCommandService matchQueueCommandService =
            new MatchQueueCommandService(matchQueueStore, userStatusStore);

    @Test
    @DisplayName("IN_GAME 상태가 남아 있으면 명시적 joinQueue도 차단된다")
    void joinQueue_InGameStatusRemains_Blocked() {
        // given
        userStatusStore.updateStatus(USER_A_ID, MatchStatus.IN_GAME, 1_800L);

        // when & then
        assertThatThrownBy(() -> matchQueueCommandService.joinQueue(USER_A_ID, TIER_SCORE))
                .isInstanceOf(MatchingException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.ALREADY_IN_QUEUE);
        verify(matchQueueStore, never()).add(any(MatchTicket.class));
    }

    @Test
    @DisplayName("정상 종료 cleanup은 자동 큐 복귀 없이 IN_GAME만 제거하고 이후 명시적 joinQueue를 허용한다")
    void removeFinishedGameStatuses_AfterCleanup_ExplicitJoinQueueAllowed() {
        // given
        userStatusStore.updateStatus(USER_A_ID, MatchStatus.IN_GAME, 1_800L);
        userStatusStore.updateStatus(USER_B_ID, MatchStatus.IN_GAME, 1_800L);

        // when
        matchUserStatusCommandService.removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
        matchQueueCommandService.joinQueue(USER_A_ID, TIER_SCORE);

        // then
        assertThat(userStatusStore.getStatus(USER_A_ID)).contains(MatchStatus.MATCHING);
        assertThat(userStatusStore.getStatus(USER_B_ID)).isEmpty();
        verify(matchQueueStore).add(any(MatchTicket.class));
    }

    private static class InMemoryMatchUserStatusStore implements MatchUserStatusStore {

        private final Map<Long, MatchStatus> statuses = new HashMap<>();

        @Override
        public boolean setStatusIfAbsent(Long userId, MatchStatus status, long ttlSeconds) {
            if (statuses.containsKey(userId)) {
                return false;
            }
            statuses.put(userId, status);
            return true;
        }

        @Override
        public Optional<MatchStatus> getStatus(Long userId) {
            return Optional.ofNullable(statuses.get(userId));
        }

        @Override
        public void updateStatus(Long userId, MatchStatus status, long ttlSeconds) {
            statuses.put(userId, status);
        }

        @Override
        public void removeStatus(Long userId) {
            statuses.remove(userId);
        }
    }
}
