package com.sang.smite.match.service;

import com.sang.smite.matching.command.MatchResponseCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchResponseServiceTest {

    private final MatchResponseCommandService matchResponseCommandService = mock(MatchResponseCommandService.class);
    private final MatchResponseService matchResponseService = new MatchResponseService(matchResponseCommandService);

    @Test
    @DisplayName("매칭 수락 요청을 matching 모듈 서비스에 위임한다.")
    void accept() {
        matchResponseService.accept("match-1", 1L);

        verify(matchResponseCommandService).accept("match-1", 1L);
    }

    @Test
    @DisplayName("매칭 거절 요청을 matching 모듈 서비스에 위임한다.")
    void reject() {
        matchResponseService.reject("match-1", 1L);

        verify(matchResponseCommandService).reject("match-1", 1L);
    }
}
