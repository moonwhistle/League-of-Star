package com.sang.leagueofstar.game.record.service;

import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRecordRankSettlementRecoveryServiceTest {

    private static final Long ZERO_RECORD_GAME_ROOM_ID = 100L;
    private static final Long ONE_RECORD_GAME_ROOM_ID = 101L;
    private static final Long SETTLED_GAME_ROOM_ID = 102L;

    @InjectMocks
    private GameRecordRankSettlementRecoveryService recoveryService;

    @Mock
    private GameRecordRankSettlementService gameRecordRankSettlementService;

    @Mock
    private FinishedGameMatchStatusCleanupService finishedGameMatchStatusCleanupService;

    @Test
    @DisplayName("recoverUnsettledFinishedGameRooms - record count 0은 재정산 후 cleanup하고 2는 cleanup만 한다")
    void recoverUnsettledFinishedGameRooms_OnlyZeroRecordSettled() {
        // given
        given(gameRecordRankSettlementService.findUnsettledFinishedGameRoomIds(
                GameRecordConstants.RECORD_RECOVERY_CANDIDATE_BATCH_SIZE
        )).willReturn(List.of(ZERO_RECORD_GAME_ROOM_ID, ONE_RECORD_GAME_ROOM_ID, SETTLED_GAME_ROOM_ID));
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(ZERO_RECORD_GAME_ROOM_ID)).willReturn(0L);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(ONE_RECORD_GAME_ROOM_ID)).willReturn(1L);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(SETTLED_GAME_ROOM_ID))
                .willReturn(GameRecordConstants.SETTLED_RECORD_COUNT);

        // when
        recoveryService.recoverUnsettledFinishedGameRooms();

        // then
        verify(gameRecordRankSettlementService).settleFinishedGameRoom(ZERO_RECORD_GAME_ROOM_ID);
        verify(gameRecordRankSettlementService, never()).settleFinishedGameRoom(ONE_RECORD_GAME_ROOM_ID);
        verify(gameRecordRankSettlementService, never()).settleFinishedGameRoom(SETTLED_GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService).cleanupIfSettled(ZERO_RECORD_GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService).cleanupIfSettled(SETTLED_GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService, never()).cleanupIfSettled(ONE_RECORD_GAME_ROOM_ID);
    }

    @Test
    @DisplayName("recoverUnsettledFinishedGameRooms - 재정산 실패가 다음 후보 처리를 막지 않는다")
    void recoverUnsettledFinishedGameRooms_SettlementFailed_ContinueNext() {
        // given
        given(gameRecordRankSettlementService.findUnsettledFinishedGameRoomIds(
                GameRecordConstants.RECORD_RECOVERY_CANDIDATE_BATCH_SIZE
        )).willReturn(List.of(ZERO_RECORD_GAME_ROOM_ID, SETTLED_GAME_ROOM_ID));
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(ZERO_RECORD_GAME_ROOM_ID)).willReturn(0L);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(SETTLED_GAME_ROOM_ID))
                .willReturn(GameRecordConstants.SETTLED_RECORD_COUNT);
        willThrow(new RuntimeException("failed"))
                .given(gameRecordRankSettlementService)
                .settleFinishedGameRoom(ZERO_RECORD_GAME_ROOM_ID);

        // when
        recoveryService.recoverUnsettledFinishedGameRooms();

        // then
        verify(gameRecordRankSettlementService).settleFinishedGameRoom(ZERO_RECORD_GAME_ROOM_ID);
        verify(gameRecordRankSettlementService, never()).settleFinishedGameRoom(SETTLED_GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService, never()).cleanupIfSettled(ZERO_RECORD_GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService).cleanupIfSettled(SETTLED_GAME_ROOM_ID);
    }

    @Test
    @DisplayName("recoverUnsettledFinishedGameRooms - cleanup 실패가 다음 후보 처리를 막지 않는다")
    void recoverUnsettledFinishedGameRooms_CleanupFailed_ContinueNext() {
        // given
        given(gameRecordRankSettlementService.findUnsettledFinishedGameRoomIds(
                GameRecordConstants.RECORD_RECOVERY_CANDIDATE_BATCH_SIZE
        )).willReturn(List.of(ZERO_RECORD_GAME_ROOM_ID, SETTLED_GAME_ROOM_ID));
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(ZERO_RECORD_GAME_ROOM_ID)).willReturn(0L);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(SETTLED_GAME_ROOM_ID))
                .willReturn(GameRecordConstants.SETTLED_RECORD_COUNT);
        willThrow(new RuntimeException("cleanup failed"))
                .given(finishedGameMatchStatusCleanupService)
                .cleanupIfSettled(ZERO_RECORD_GAME_ROOM_ID);

        // when
        recoveryService.recoverUnsettledFinishedGameRooms();

        // then
        verify(gameRecordRankSettlementService).settleFinishedGameRoom(ZERO_RECORD_GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService).cleanupIfSettled(ZERO_RECORD_GAME_ROOM_ID);
        verify(finishedGameMatchStatusCleanupService).cleanupIfSettled(SETTLED_GAME_ROOM_ID);
    }
}
