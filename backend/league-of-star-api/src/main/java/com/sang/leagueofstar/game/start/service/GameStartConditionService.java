package com.sang.leagueofstar.game.start.service;

import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.game.rtt.domain.GameRttStartReadyState;
import com.sang.leagueofstar.game.rtt.service.GameRttMeasurementService;
import com.sang.leagueofstar.game.start.domain.GameStartBlockedReason;
import com.sang.leagueofstar.game.start.domain.GameStartReadyResult;
import com.sang.leagueofstar.game.websocket.session.GameRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameStartConditionService {

    private final GameRttMeasurementService gameRttMeasurementService;
    private final GameRoomReadService gameRoomReadService;
    private final GameRoomWebSocketSessionRegistry sessionRegistry;

    public GameStartReadyResult checkStartReady(Long gameRoomId) {
        Optional<GameRttStartReadyState> rttState = gameRttMeasurementService.findStartReadyState(gameRoomId);
        if (rttState.isEmpty()) {
            return GameStartReadyResult.blocked(GameStartBlockedReason.RTT_NOT_READY);
        }

        if (!isReadyGameRoom(gameRoomId)) {
            return GameStartReadyResult.blocked(GameStartBlockedReason.GAME_ROOM_NOT_READY);
        }

        if (!hasBothParticipantSessions(rttState.get())) {
            return GameStartReadyResult.blocked(GameStartBlockedReason.WEB_SOCKET_SESSION_NOT_READY);
        }

        return GameStartReadyResult.ready(rttState.get());
    }

    private boolean isReadyGameRoom(Long gameRoomId) {
        try {
            return gameRoomReadService.getStatus(gameRoomId) == GameStatus.READY;
        } catch (CoreException e) {
            return false;
        }
    }

    private boolean hasBothParticipantSessions(GameRttStartReadyState rttState) {
        return sessionRegistry.isConnected(rttState.gameRoomId(), rttState.userAId())
                && sessionRegistry.isConnected(rttState.gameRoomId(), rttState.userBId());
    }
}
