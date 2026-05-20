package com.sang.smite.game.start.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.rtt.domain.GameRttStartReadyState;
import com.sang.smite.game.rtt.service.GameRttMeasurementService;
import com.sang.smite.game.start.domain.GameStartBlockedReason;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStartConditionServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_A_ID = 1L;
    private static final Long USER_B_ID = 2L;

    private final GameRttMeasurementService gameRttMeasurementService = mock(GameRttMeasurementService.class);
    private final GameRoomReadService gameRoomReadService = mock(GameRoomReadService.class);
    private final GameRoomWebSocketSessionRegistry sessionRegistry = mock(GameRoomWebSocketSessionRegistry.class);
    private final GameStartConditionService service = new GameStartConditionService(
            gameRttMeasurementService,
            gameRoomReadService,
            sessionRegistry
    );

    @Test
    @DisplayName("checkStartReady - RTT PASSED, gameRoom READY, 양쪽 session 존재 시 시작 가능하다")
    void checkStartReady_Ready() {
        // given
        GameRttStartReadyState rttState = rttState();
        when(gameRttMeasurementService.findStartReadyState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(sessionRegistry.isConnected(GAME_ROOM_ID, USER_A_ID)).thenReturn(true);
        when(sessionRegistry.isConnected(GAME_ROOM_ID, USER_B_ID)).thenReturn(true);

        // when
        var result = service.checkStartReady(GAME_ROOM_ID);

        // then
        assertThat(result.ready()).isTrue();
        assertThat(result.blockedReason()).isNull();
        assertThat(result.rttState()).isEqualTo(rttState);
    }

    @Test
    @DisplayName("checkStartReady - RTT start ready 상태가 없으면 시작을 차단한다")
    void checkStartReady_RttNotReady() {
        // given
        when(gameRttMeasurementService.findStartReadyState(GAME_ROOM_ID)).thenReturn(Optional.empty());

        // when
        var result = service.checkStartReady(GAME_ROOM_ID);

        // then
        assertThat(result.ready()).isFalse();
        assertThat(result.blockedReason()).isEqualTo(GameStartBlockedReason.RTT_NOT_READY);
    }

    @Test
    @DisplayName("checkStartReady - gameRoom이 READY가 아니면 시작을 차단한다")
    void checkStartReady_GameRoomNotReady() {
        // given
        when(gameRttMeasurementService.findStartReadyState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.IN_PROGRESS);

        // when
        var result = service.checkStartReady(GAME_ROOM_ID);

        // then
        assertThat(result.ready()).isFalse();
        assertThat(result.blockedReason()).isEqualTo(GameStartBlockedReason.GAME_ROOM_NOT_READY);
    }

    @Test
    @DisplayName("checkStartReady - gameRoom 조회 예외가 발생하면 시작을 차단한다")
    void checkStartReady_GameRoomNotFound() {
        // given
        when(gameRttMeasurementService.findStartReadyState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenThrow(new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        // when
        var result = service.checkStartReady(GAME_ROOM_ID);

        // then
        assertThat(result.ready()).isFalse();
        assertThat(result.blockedReason()).isEqualTo(GameStartBlockedReason.GAME_ROOM_NOT_READY);
    }

    @Test
    @DisplayName("checkStartReady - RTT 통과 유저 중 한 명의 local session이 없으면 시작을 차단한다")
    void checkStartReady_WebSocketSessionNotReady() {
        // given
        when(gameRttMeasurementService.findStartReadyState(GAME_ROOM_ID)).thenReturn(Optional.of(rttState()));
        when(gameRoomReadService.getStatus(GAME_ROOM_ID)).thenReturn(GameStatus.READY);
        when(sessionRegistry.isConnected(GAME_ROOM_ID, USER_A_ID)).thenReturn(true);
        when(sessionRegistry.isConnected(GAME_ROOM_ID, USER_B_ID)).thenReturn(false);

        // when
        var result = service.checkStartReady(GAME_ROOM_ID);

        // then
        assertThat(result.ready()).isFalse();
        assertThat(result.blockedReason()).isEqualTo(GameStartBlockedReason.WEB_SOCKET_SESSION_NOT_READY);
        verify(sessionRegistry).isConnected(GAME_ROOM_ID, USER_A_ID);
        verify(sessionRegistry).isConnected(GAME_ROOM_ID, USER_B_ID);
    }

    private GameRttStartReadyState rttState() {
        return new GameRttStartReadyState(GAME_ROOM_ID, USER_A_ID, USER_B_ID, 35L, 45L);
    }
}
