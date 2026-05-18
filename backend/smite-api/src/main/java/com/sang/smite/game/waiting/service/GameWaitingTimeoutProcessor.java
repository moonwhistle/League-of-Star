package com.sang.smite.game.waiting.service;

import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.waiting.common.constant.GameWaitingConstants;
import com.sang.smite.game.waiting.domain.GameWaitingState;
import com.sang.smite.game.waiting.repository.GameWaitingStore;
import com.sang.smite.matching.command.MatchUserStatusCommandService;
import com.sang.smite.redis.lock.annotation.DistributedRedisLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 단일 gameRoom waiting timeout 정산을 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class GameWaitingTimeoutProcessor {

    private final GameWaitingStore gameWaitingStore;
    private final GameRoomReadService gameRoomReadService;
    private final GameRoomCommandService gameRoomCommandService;
    private final MatchUserStatusCommandService matchUserStatusCommandService;

    @DistributedRedisLock(key = "'" + GameWaitingConstants.WAITING_TIMEOUT_LOCK_KEY_PREFIX + "' + #gameRoomId")
    public void processTimeoutWithLock(Long gameRoomId) {
        Optional<GameWaitingState> waitingState = gameWaitingStore.findWaitingState(gameRoomId);
        if (waitingState.isEmpty()) {
            gameWaitingStore.cleanup(gameRoomId);
            return;
        }

        if (waitingState.get().bothReady()) {
            gameWaitingStore.cleanup(gameRoomId);
            return;
        }

        GameWaitingState state = waitingState.get();
        GameStatus gameStatus = gameRoomReadService.getStatus(gameRoomId);
        if (gameStatus == GameStatus.READY) {
            boolean aborted = gameRoomCommandService.abortReadyRoomIfReady(gameRoomId);
            if (!aborted) {
                return;
            }
            removeMatchStatuses(state);
            gameWaitingStore.cleanup(gameRoomId);
            return;
        }

        if (gameStatus == GameStatus.ABORTED) {
            removeMatchStatuses(state);
            gameWaitingStore.cleanup(gameRoomId);
            return;
        }

        gameWaitingStore.cleanup(gameRoomId);
    }

    private void removeMatchStatuses(GameWaitingState state) {
        matchUserStatusCommandService.removeGameWaitingTimeoutStatuses(state.userAId(), state.userBId());
    }
}
