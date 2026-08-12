package com.sang.leagueofstar.game.record.outbox.scheduler;

import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameSettlementOutboxSchedulerTest {

    private final GameSettlementOutboxWorker worker = mock(GameSettlementOutboxWorker.class);
    private final GameSettlementOutboxScheduler scheduler = new GameSettlementOutboxScheduler(worker);

    @Test
    @DisplayName("processPendingOutbox - 주기마다 Outbox Worker를 실행한다")
    void processPendingOutbox_RunWorker() {
        scheduler.processPendingOutbox();

        verify(worker).processPendingBatch();
    }

    @Test
    @DisplayName("processPendingOutbox - 한 번의 polling 실패가 다음 스케줄 실행을 막지 않는다")
    void processPendingOutbox_FailureIsolated() {
        doThrow(new RuntimeException("failed")).when(worker).processPendingBatch();

        assertDoesNotThrow(scheduler::processPendingOutbox);
    }
}
