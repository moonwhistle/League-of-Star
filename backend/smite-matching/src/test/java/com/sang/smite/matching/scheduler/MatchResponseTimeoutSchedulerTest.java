package com.sang.smite.matching.scheduler;

import com.sang.smite.matching.service.MatchResponseTimeoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchResponseTimeoutSchedulerTest {

    @InjectMocks
    private MatchResponseTimeoutScheduler scheduler;

    @Mock
    private MatchResponseTimeoutService matchResponseTimeoutService;

    @Test
    @DisplayName("스케줄러는 timeout 처리 서비스를 호출한다")
    void processTimeouts() {
        scheduler.processTimeouts();

        verify(matchResponseTimeoutService).processTimeouts();
    }
}
