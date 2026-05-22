package com.sang.smite.game.end.scheduler;

import com.sang.smite.game.end.service.GameEndSettlementService;
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
class GameEndSchedulerTest {

    @InjectMocks
    private GameEndScheduler scheduler;

    @Mock
    private GameEndSettlementService gameEndSettlementService;

    @Test
    @DisplayName("processDueEndDeadlines - game end 정산 서비스를 호출한다")
    void processDueEndDeadlines() {
        // when
        scheduler.processDueEndDeadlines();

        // then
        verify(gameEndSettlementService).processDueEndDeadlines();
    }

    @Test
    @DisplayName("processDueEndDeadlines - 정산 중 예외가 발생해도 전파하지 않는다")
    void processDueEndDeadlines_Exception() {
        // given
        doThrow(new RuntimeException("failed"))
                .when(gameEndSettlementService)
                .processDueEndDeadlines();

        // when & then
        assertThatCode(() -> scheduler.processDueEndDeadlines()).doesNotThrowAnyException();
    }
}
