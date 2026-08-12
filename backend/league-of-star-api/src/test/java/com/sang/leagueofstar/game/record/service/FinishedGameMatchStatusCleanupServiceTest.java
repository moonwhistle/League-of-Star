package com.sang.leagueofstar.game.record.service;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private GameRecordRankSettlementService settlementService;

    @Mock
    private MatchUserStatusCommandService matchUserStatusCommandService;

    @Test
    @DisplayName("cleanupSettledGame - FINISHED이고 전적 2건이면 참가자 IN_GAME 상태를 제거한다")
    void cleanupSettledGame_FinishedAndSettled_CleanupStatuses() {
        givenSettledMatch(List.of(USER_A_ID, USER_B_ID));

        cleanupService.cleanupSettledGame(GAME_ROOM_ID);

        verify(matchUserStatusCommandService).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupSettledGame - 정산 전이면 실패를 전파하여 Outbox 재시도 대상으로 남긴다")
    void cleanupSettledGame_SettlementIncomplete_ThrowException() {
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.FINISHED);
        given(settlementService.countRecordsByGameRoomId(GAME_ROOM_ID)).willReturn(0L);

        assertThatThrownBy(() -> cleanupService.cleanupSettledGame(GAME_ROOM_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recordCount=0");

        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    @Test
    @DisplayName("cleanupSettledGame - Redis 정리 실패를 Worker까지 전파한다")
    void cleanupSettledGame_RedisCleanupFailed_PropagateException() {
        givenSettledMatch(List.of(USER_A_ID, USER_B_ID));
        willThrow(new RuntimeException("redis failed"))
                .given(matchUserStatusCommandService)
                .removeFinishedGameStatuses(USER_A_ID, USER_B_ID);

        assertThatThrownBy(() -> cleanupService.cleanupSettledGame(GAME_ROOM_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis failed");
    }

    @Test
    @DisplayName("cleanupSettledGame - 참가자 수가 잘못되면 실패를 전파한다")
    void cleanupSettledGame_InvalidParticipants_ThrowException() {
        givenSettledMatch(List.of(USER_A_ID));

        assertThatThrownBy(() -> cleanupService.cleanupSettledGame(GAME_ROOM_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid participants");
    }

    @Test
    @DisplayName("cleanupSettledGame - CUSTOM 게임은 Redis 매칭 상태 정리 대상이 아니다")
    void cleanupSettledGame_CustomGame_NoOp() {
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.CUSTOM);

        cleanupService.cleanupSettledGame(GAME_ROOM_ID);

        verify(gameRoomReadService, never()).getStatus(GAME_ROOM_ID);
        verify(settlementService, never()).countRecordsByGameRoomId(GAME_ROOM_ID);
        verify(matchUserStatusCommandService, never()).removeFinishedGameStatuses(USER_A_ID, USER_B_ID);
    }

    private void givenSettledMatch(List<Long> participantUserIds) {
        given(gameRoomReadService.getMode(GAME_ROOM_ID)).willReturn(GameMode.MATCH);
        given(gameRoomReadService.getStatus(GAME_ROOM_ID)).willReturn(GameStatus.FINISHED);
        given(settlementService.countRecordsByGameRoomId(GAME_ROOM_ID))
                .willReturn(GameRecordConstants.SETTLED_RECORD_COUNT);
        given(gameRoomReadService.getParticipantUserIds(GAME_ROOM_ID)).willReturn(participantUserIds);
    }
}
