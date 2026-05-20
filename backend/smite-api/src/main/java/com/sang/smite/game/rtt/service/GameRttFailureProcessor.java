package com.sang.smite.game.rtt.service;

import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.rtt.common.constant.GameRttConstants;
import com.sang.smite.game.rtt.domain.GameRttFailureReason;
import com.sang.smite.game.rtt.domain.GameRttState;
import com.sang.smite.game.rtt.repository.GameRttMeasurementStore;
import com.sang.smite.game.websocket.service.GameStartFailedWebSocketSender;
import com.sang.smite.matching.command.MatchUserStatusCommandService;
import com.sang.smite.redis.lock.annotation.DistributedRedisLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameRttFailureProcessor {

    private final GameRttMeasurementStore gameRttMeasurementStore;
    private final GameRttPingTracker gameRttPingTracker;
    private final GameRoomReadService gameRoomReadService;
    private final GameRoomCommandService gameRoomCommandService;
    private final MatchUserStatusCommandService matchUserStatusCommandService;
    private final GameStartFailedWebSocketSender gameStartFailedWebSocketSender;

    @DistributedRedisLock(key = "'" + GameRttConstants.RTT_FAILURE_LOCK_KEY_PREFIX + "' + #gameRoomId")
    public void processFailureWithLock(Long gameRoomId, GameRttFailureReason reason) {
        Optional<GameRttState> rttState = gameRttMeasurementStore.findState(gameRoomId);
        if (rttState.isEmpty()) {
            gameRttPingTracker.cleanup(gameRoomId);
            return;
        }

        GameRttState state = rttState.get();
        if (!state.hasFailed()) {
            return;
        }

        GameStatus gameStatus = gameRoomReadService.getStatus(gameRoomId);
        if (gameStatus == GameStatus.READY) {
            boolean aborted = gameRoomCommandService.abortReadyRoomIfReady(gameRoomId);
            if (!aborted) {
                return;
            }
            cleanupFailureState(state, reason);
            return;
        }

        if (gameStatus == GameStatus.ABORTED) {
            cleanupFailureState(state, reason);
            return;
        }

        cleanupRttState(state.gameRoomId());
    }

    private void cleanupFailureState(GameRttState state, GameRttFailureReason reason) {
        matchUserStatusCommandService.removeGameStartFailureStatuses(state.userAId(), state.userBId());
        gameStartFailedWebSocketSender.sendFailure(
                state.gameRoomId(),
                reason.name(),
                GameRttConstants.GAME_START_FAILED_ACTION
        );
        cleanupRttState(state.gameRoomId());
    }

    private void cleanupRttState(Long gameRoomId) {
        gameRttPingTracker.cleanup(gameRoomId);
        gameRttMeasurementStore.cleanup(gameRoomId);
    }
}
