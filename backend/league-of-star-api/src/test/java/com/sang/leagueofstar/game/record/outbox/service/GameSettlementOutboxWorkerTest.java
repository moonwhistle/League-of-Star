package com.sang.leagueofstar.game.record.outbox.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameSettlementOutboxWorkerTest {

    private static final ClaimedGameSettlementOutbox CLAIMED = new ClaimedGameSettlementOutbox(
            "event-id",
            100L,
            "lock-token",
            0
    );

    @InjectMocks
    private GameSettlementOutboxWorker worker;

    @Mock
    private GameSettlementOutboxClaimService claimService;

    @Mock
    private GameSettlementOutboxProcessor processor;

    @Test
    @DisplayName("processImmediately - AFTER_COMMIT 이벤트의 Outbox를 선점한 경우 즉시 처리한다")
    void processImmediately_Claimed_Process() {
        given(claimService.claim(CLAIMED.eventId())).willReturn(Optional.of(CLAIMED));

        worker.processImmediately(CLAIMED.eventId());

        verify(processor).process(CLAIMED);
    }

    @Test
    @DisplayName("processPendingBatch - Scheduler가 선점한 작업을 모두 처리한다")
    void processPendingBatch_ProcessClaimedEvents() {
        ClaimedGameSettlementOutbox second = new ClaimedGameSettlementOutbox(
                "event-id-2", 101L, "lock-token-2", 1
        );
        given(claimService.claimBatch(100)).willReturn(List.of(CLAIMED, second));

        int processed = worker.processPendingBatch();

        assertThat(processed).isEqualTo(2);
        verify(processor).process(CLAIMED);
        verify(processor).process(second);
    }
}
