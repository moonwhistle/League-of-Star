package com.sang.leagueofstar.game.record.scheduler;

import com.sang.leagueofstar.game.record.service.GameRecordRankSettlementRecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRecordRankSettlementRecoverySchedulerTest {

    @InjectMocks
    private GameRecordRankSettlementRecoveryScheduler scheduler;

    @Mock
    private GameRecordRankSettlementRecoveryService gameRecordRankSettlementRecoveryService;

    @Test
    @DisplayName("recoverUnsettledFinishedGameRooms - record/rank 복구 서비스를 호출한다")
    void recoverUnsettledFinishedGameRooms() {
        // when
        scheduler.recoverUnsettledFinishedGameRooms();

        // then
        verify(gameRecordRankSettlementRecoveryService).recoverUnsettledFinishedGameRooms();
    }

    @Test
    @DisplayName("recoverUnsettledFinishedGameRooms - 복구 중 예외가 발생해도 전파하지 않는다")
    void recoverUnsettledFinishedGameRooms_Exception() {
        // given
        willThrow(new RuntimeException("failed"))
                .given(gameRecordRankSettlementRecoveryService)
                .recoverUnsettledFinishedGameRooms();

        // when & then
        assertThatCode(() -> scheduler.recoverUnsettledFinishedGameRooms()).doesNotThrowAnyException();
    }
}
