package com.sang.leagueofstar.game.record.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import com.sang.leagueofstar.matching.command.MatchUserStatusCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinishedGameMatchStatusCleanupServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    @InjectMocks
    private FinishedGameMatchStatusCleanupService cleanupService;

    @Mock
    private GameRoomReadService gameRoomReadService;

    @Mock
    private GameRecordRankSettlementService gameRecordRankSettlementService;

    @Mock
    private MatchUserStatusCommandService matchUserStatusCommandService;

    @Test
    @DisplayName("cleanupIfSettled - FINISHED이고 record 2행이면 참가자 IN_GAME cleanup을 요청한다")
    void cleanupIfSettled_FinishedAndSettled_CleanupStatuses() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.FINISHED);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(GAME_ROOM_ID))
                .willReturn(GameRecordConstants.SETTLED_RECORD_COUNT);
        given(gameRoomReadService.getParticipantUserIds(GAME_ROOM_ID)).willReturn(List.of(USER_A_ID, USER_B_ID));

        // when
        cleanupService.cleanupIfSettled(GAME_ROOM_ID);

        // then
        verify(matchUserStatusCommandService).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - FINISHED가 아니면 cleanup하지 않는다")
    void cleanupIfSettled_NotFinished_NoCleanup() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.IN_PROGRESS);

        // when
        cleanupService.cleanupIfSettled(GAME_ROOM_ID);

        // then
        verify(gameRecordRankSettlementService, never()).countRecordsByGameRoomId(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - record count 0이면 record/rank 복구 대상으로 보고 cleanup하지 않는다")
    void cleanupIfSettled_RecordCountZero_NoCleanup() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.FINISHED);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(GAME_ROOM_ID)).willReturn(0L);

        // when
        cleanupService.cleanupIfSettled(GAME_ROOM_ID);

        // then
        verify(gameRoomReadService, never()).getParticipantUserIds(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - record count 1이면 불완전 정산으로 보고 cleanup하지 않는다")
    void cleanupIfSettled_RecordCountOne_NoCleanup() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.FINISHED);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(GAME_ROOM_ID)).willReturn(1L);

        // when
        cleanupService.cleanupIfSettled(GAME_ROOM_ID);

        // then
        verify(gameRoomReadService, never()).getParticipantUserIds(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - 참가자 수가 2명이 아니면 cleanup하지 않는다")
    void cleanupIfSettled_InvalidParticipantCount_NoCleanup() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.FINISHED);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(GAME_ROOM_ID))
                .willReturn(GameRecordConstants.SETTLED_RECORD_COUNT);
        given(gameRoomReadService.getParticipantUserIds(GAME_ROOM_ID)).willReturn(List.of(USER_A_ID));

        // when
        cleanupService.cleanupIfSettled(GAME_ROOM_ID);

        // then
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - matching cleanup 실패는 호출자에게 전파하지 않는다")
    void cleanupIfSettled_MatchingCleanupFailed_SwallowException() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.FINISHED);
        given(gameRecordRankSettlementService.countRecordsByGameRoomId(GAME_ROOM_ID))
                .willReturn(GameRecordConstants.SETTLED_RECORD_COUNT);
        given(gameRoomReadService.getParticipantUserIds(GAME_ROOM_ID)).willReturn(List.of(USER_A_ID, USER_B_ID));
        willThrow(new RuntimeException("redis failed"))
                .given(matchUserStatusCommandService)
                .removeFinishedGameStatuses(USER_A_ID, USER_B_ID);

        // when & then
        assertDoesNotThrow(() -> cleanupService.cleanupIfSettled(GAME_ROOM_ID));
        verify(matchUserStatusCommandService).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - 상태 조회 실패도 호출자에게 전파하지 않는다")
    void cleanupIfSettled_StatusLoadFailed_SwallowException() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID))
                .willThrow(new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        // when & then
        assertDoesNotThrow(() -> cleanupService.cleanupIfSettled(GAME_ROOM_ID));
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - PRACTICE gameRoom이면 match status cleanup 대상으로 보지 않는다")
    void cleanupIfSettled_PracticeRoom_NoCleanup() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.PRACTICE);

        // when
        cleanupService.cleanupIfSettled(GAME_ROOM_ID);

        // then
        verify(gameRoomReadService, never()).getStatus(GAME_ROOM_ID);
        verify(gameRecordRankSettlementService, never()).countRecordsByGameRoomId(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupIfSettled - CUSTOM gameRoom이면 match status cleanup 대상으로 보지 않는다")
    void cleanupIfSettled_CustomRoom_NoCleanup() {
        // given
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.CUSTOM);

        // when
        cleanupService.cleanupIfSettled(GAME_ROOM_ID);

        // then
        verify(gameRoomReadService, never()).getStatus(GAME_ROOM_ID);
        verify(gameRecordRankSettlementService, never()).countRecordsByGameRoomId(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }
}
