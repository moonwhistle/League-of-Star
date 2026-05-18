package com.sang.smite.matching.command;

import com.sang.smite.matching.repository.MatchUserStatusStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchUserStatusCommandServiceTest {

    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    private final MatchUserStatusStore userStatusStore = mock(MatchUserStatusStore.class);
    private final MatchUserStatusCommandService service = new MatchUserStatusCommandService(userStatusStore);

    @Test
    @DisplayName("removeGameWaitingTimeoutStatuses - 두 유저의 match status를 제거한다")
    void removeGameWaitingTimeoutStatuses() {
        // when
        service.removeGameWaitingTimeoutStatuses(USER_A_ID, USER_B_ID);

        // then
        verify(userStatusStore).removeStatus(USER_A_ID);
        verify(userStatusStore).removeStatus(USER_B_ID);
    }
}
