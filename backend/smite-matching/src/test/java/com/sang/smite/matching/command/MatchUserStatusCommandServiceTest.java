package com.sang.smite.matching.command;

import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchUserStatusCommandServiceTest {

    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    private final MatchUserStatusStore userStatusStore = mock(MatchUserStatusStore.class);
    private final MatchUserStatusCommandService service = new MatchUserStatusCommandService(userStatusStore);

    @Test
    @DisplayName("removeGameWaitingTimeoutStatuses - IN_GAME 상태인 두 유저의 match status를 제거한다")
    void removeGameWaitingTimeoutStatuses_InGame() {
        // given
        when(userStatusStore.getStatus(USER_A_ID)).thenReturn(Optional.of(MatchStatus.IN_GAME));
        when(userStatusStore.getStatus(USER_B_ID)).thenReturn(Optional.of(MatchStatus.IN_GAME));

        // when
        service.removeGameWaitingTimeoutStatuses(USER_A_ID, USER_B_ID);

        // then
        verify(userStatusStore).removeStatus(USER_A_ID);
        verify(userStatusStore).removeStatus(USER_B_ID);
    }

    @Test
    @DisplayName("removeGameWaitingTimeoutStatuses - 이미 새 매칭을 시작한 유저의 match status는 제거하지 않는다")
    void removeGameWaitingTimeoutStatuses_NotInGame() {
        // given
        when(userStatusStore.getStatus(USER_A_ID)).thenReturn(Optional.of(MatchStatus.MATCHING));
        when(userStatusStore.getStatus(USER_B_ID)).thenReturn(Optional.empty());

        // when
        service.removeGameWaitingTimeoutStatuses(USER_A_ID, USER_B_ID);

        // then
        verify(userStatusStore, never()).removeStatus(USER_A_ID);
        verify(userStatusStore, never()).removeStatus(USER_B_ID);
    }

    @Test
    @DisplayName("removeFinishedGameStatuses - IN_GAME 상태인 두 유저의 match status를 제거한다")
    void removeFinishedGameStatuses_InGame() {
        // given
        when(userStatusStore.getStatus(USER_A_ID)).thenReturn(Optional.of(MatchStatus.IN_GAME));
        when(userStatusStore.getStatus(USER_B_ID)).thenReturn(Optional.of(MatchStatus.IN_GAME));

        // when
        service.removeFinishedGameStatuses(USER_A_ID, USER_B_ID);

        // then
        verify(userStatusStore).removeStatus(USER_A_ID);
        verify(userStatusStore).removeStatus(USER_B_ID);
    }

    @Test
    @DisplayName("removeFinishedGameStatuses - IN_GAME이 아닌 새 매칭 상태는 제거하지 않는다")
    void removeFinishedGameStatuses_NotInGame() {
        // given
        when(userStatusStore.getStatus(USER_A_ID)).thenReturn(Optional.of(MatchStatus.MATCHING));
        when(userStatusStore.getStatus(USER_B_ID)).thenReturn(Optional.of(MatchStatus.FOUND));

        // when
        service.removeFinishedGameStatuses(USER_A_ID, USER_B_ID);

        // then
        verify(userStatusStore, never()).removeStatus(USER_A_ID);
        verify(userStatusStore, never()).removeStatus(USER_B_ID);
    }

    @Test
    @DisplayName("removeFinishedGameStatuses - 한 유저가 이미 정리되어도 남은 IN_GAME 상태는 제거한다")
    void removeFinishedGameStatuses_PartialInGame() {
        // given
        when(userStatusStore.getStatus(USER_A_ID)).thenReturn(Optional.of(MatchStatus.IN_GAME));
        when(userStatusStore.getStatus(USER_B_ID)).thenReturn(Optional.empty());

        // when
        service.removeFinishedGameStatuses(USER_A_ID, USER_B_ID);

        // then
        verify(userStatusStore).removeStatus(USER_A_ID);
        verify(userStatusStore, never()).removeStatus(USER_B_ID);
    }
}
