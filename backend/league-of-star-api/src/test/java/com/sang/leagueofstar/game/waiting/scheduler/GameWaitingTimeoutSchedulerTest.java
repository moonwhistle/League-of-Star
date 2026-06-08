package com.sang.leagueofstar.game.waiting.scheduler;

import com.sang.leagueofstar.game.waiting.service.GameWaitingTimeoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameWaitingTimeoutSchedulerTest {

    @InjectMocks
    private GameWaitingTimeoutScheduler scheduler;

    @Mock
    private GameWaitingTimeoutService gameWaitingTimeoutService;

    @Test
    @DisplayName("스케줄러는 game waiting timeout 처리 서비스를 호출한다")
    void processTimeouts() {
        // when
        scheduler.processTimeouts();

        // then
        verify(gameWaitingTimeoutService).processTimeouts();
    }
}
