package com.sang.leagueofstar.game.record.outbox.service;

import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.service.FinishedGameMatchStatusCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameSettlementOutboxProcessorTest {

    private static final ClaimedGameSettlementOutbox CLAIMED = new ClaimedGameSettlementOutbox(
            "event-id",
            100L,
            "lock-token",
            0
    );

    @InjectMocks
    private GameSettlementOutboxProcessor processor;

    @Mock
    private GameRecordRankSettlementService settlementService;

    @Mock
    private FinishedGameMatchStatusCleanupService cleanupService;

    @Mock
    private GameSettlementOutboxStateService stateService;

    @Test
    @DisplayName("process - DB 정산과 Redis 정리가 끝난 뒤 Outbox를 완료한다")
    void process_Success_CompleteOutboxLast() {
        given(stateService.complete(CLAIMED)).willReturn(true);

        processor.process(CLAIMED);

        InOrder inOrder = inOrder(settlementService, cleanupService, stateService);
        inOrder.verify(settlementService).settleFinishedGameRoom(CLAIMED.gameRoomId());
        inOrder.verify(cleanupService).cleanupSettledGame(CLAIMED.gameRoomId());
        inOrder.verify(stateService).complete(CLAIMED);
    }

    @Test
    @DisplayName("process - 정산 실패 시 Redis 정리 없이 Outbox를 재시도 상태로 변경한다")
    void process_SettlementFailed_MarkFailed() {
        RuntimeException failure = new RuntimeException("settlement failed");
        willThrow(failure).given(settlementService).settleFinishedGameRoom(CLAIMED.gameRoomId());
        given(stateService.fail(CLAIMED, failure)).willReturn(true);

        processor.process(CLAIMED);

        verify(cleanupService, never()).cleanupSettledGame(CLAIMED.gameRoomId());
        verify(stateService).fail(CLAIMED, failure);
        verify(stateService, never()).complete(CLAIMED);
    }

    @Test
    @DisplayName("process - Redis 정리 실패도 Outbox를 완료하지 않고 재시도한다")
    void process_RedisCleanupFailed_MarkFailed() {
        RuntimeException failure = new RuntimeException("redis failed");
        willThrow(failure).given(cleanupService).cleanupSettledGame(CLAIMED.gameRoomId());
        given(stateService.fail(CLAIMED, failure)).willReturn(true);

        processor.process(CLAIMED);

        verify(settlementService).settleFinishedGameRoom(CLAIMED.gameRoomId());
        verify(stateService).fail(CLAIMED, failure);
        verify(stateService, never()).complete(CLAIMED);
    }
}
