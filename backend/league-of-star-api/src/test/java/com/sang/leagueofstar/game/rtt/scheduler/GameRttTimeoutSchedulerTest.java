package com.sang.leagueofstar.game.rtt.scheduler;

import com.sang.leagueofstar.game.rtt.service.GameRttMeasurementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRttTimeoutSchedulerTest {

    @InjectMocks
    private GameRttTimeoutScheduler scheduler;

    @Mock
    private GameRttMeasurementService gameRttMeasurementService;

    @Test
    @DisplayName("processTimeouts - RTT timeout 처리 서비스를 호출한다")
    void processTimeouts() {
        // when
        scheduler.processTimeouts();

        // then
        verify(gameRttMeasurementService).failTimedOutPings();
    }

    @Test
    @DisplayName("processTimeouts - timeout 처리 중 예외가 발생해도 전파하지 않는다")
    void processTimeouts_Exception() {
        // given
        doThrow(new RuntimeException("failed")).when(gameRttMeasurementService).failTimedOutPings();

        // when & then
        assertThatCode(() -> scheduler.processTimeouts()).doesNotThrowAnyException();
    }
}
