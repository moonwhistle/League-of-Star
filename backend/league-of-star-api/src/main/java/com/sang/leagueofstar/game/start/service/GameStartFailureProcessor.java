package com.sang.leagueofstar.game.start.service;

import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.game.end.service.GameEndScheduleService;
import com.sang.leagueofstar.game.rtt.domain.GameRttState;
import com.sang.leagueofstar.game.rtt.repository.GameRttMeasurementStore;
import com.sang.leagueofstar.game.rtt.service.GameRttPingTracker;
import com.sang.leagueofstar.game.start.common.constant.GameStartConstants;
import com.sang.leagueofstar.game.start.domain.GameStartFailureReason;
import com.sang.leagueofstar.game.waiting.repository.GameWaitingStore;
import com.sang.leagueofstar.game.websocket.service.GameStartFailedWebSocketSender;
import com.sang.leagueofstar.matching.command.MatchUserStatusCommandService;
import com.sang.leagueofstar.redis.lock.annotation.DistributedRedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameStartFailureProcessor {

    private final GameRoomReadService gameRoomReadService;
    private final GameRoomCommandService gameRoomCommandService;
    private final GameRttMeasurementStore gameRttMeasurementStore;
    private final GameRttPingTracker gameRttPingTracker;
    private final GameWaitingStore gameWaitingStore;
    private final GameEndScheduleService gameEndScheduleService;
    private final MatchUserStatusCommandService matchUserStatusCommandService;
    private final GameStartFailedWebSocketSender gameStartFailedWebSocketSender;

    @DistributedRedisLock(key = "'" + GameStartConstants.GAME_START_FAILURE_LOCK_KEY_PREFIX + "' + #gameRoomId")
    public void processStartedFailure(Long gameRoomId,
                                      GameStartFailureReason reason,
                                      boolean cleanupEndDeadline) {
        if (!ensureAborted(gameRoomId)) {
            return;
        }

        removeMatchStatuses(gameRoomId, findRttState(gameRoomId));
        gameStartFailedWebSocketSender.sendFailure(
                gameRoomId,
                reason.name(),
                GameStartConstants.GAME_START_FAILED_ACTION
        );
        cleanupState(gameRoomId, cleanupEndDeadline);
    }

    private boolean ensureAborted(Long gameRoomId) {
        try {
            if (gameRoomCommandService.abortInProgressRoomIfInProgress(gameRoomId)) {
                return true;
            }
            if (gameRoomReadService.getStatus(gameRoomId) == GameStatus.ABORTED) {
                return true;
            }
            log.warn("Game start failure cleanup skipped because gameRoom is not abortable. gameRoomId={}", gameRoomId);
            return false;
        } catch (RuntimeException e) {
            log.warn("Failed to abort gameRoom after game start failure. gameRoomId={}", gameRoomId, e);
            return false;
        }
    }

    private Optional<GameRttState> findRttState(Long gameRoomId) {
        try {
            return gameRttMeasurementStore.findState(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to load RTT state during game start failure cleanup. gameRoomId={}", gameRoomId, e);
            return Optional.empty();
        }
    }

    private void removeMatchStatuses(Long gameRoomId, Optional<GameRttState> rttState) {
        if (rttState.isPresent()) {
            removeMatchStatuses(gameRoomId, rttState.get().userAId(), rttState.get().userBId());
            return;
        }

        try {
            List<Long> participantUserIds = gameRoomReadService.getParticipantUserIds(gameRoomId);
            if (participantUserIds.size() == 2) {
                removeMatchStatuses(gameRoomId, participantUserIds.get(0), participantUserIds.get(1));
            }
        } catch (RuntimeException e) {
            log.warn("Failed to load participant userIds during game start failure cleanup. gameRoomId={}",
                    gameRoomId, e);
        }
    }

    private void removeMatchStatuses(Long gameRoomId, Long userAId, Long userBId) {
        try {
            matchUserStatusCommandService.removeGameStartFailureStatuses(userAId, userBId);
        } catch (RuntimeException e) {
            log.warn("Failed to remove match user statuses during game start failure cleanup. gameRoomId={}",
                    gameRoomId, e);
        }
    }

    private void cleanupState(Long gameRoomId, boolean cleanupEndDeadline) {
        cleanupRttPingTracker(gameRoomId);
        cleanupRttState(gameRoomId);
        cleanupWaitingState(gameRoomId);
        if (cleanupEndDeadline) {
            cleanupEndDeadline(gameRoomId);
        }
    }

    private void cleanupRttPingTracker(Long gameRoomId) {
        try {
            gameRttPingTracker.cleanup(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup local RTT ping tracker during game start failure cleanup. gameRoomId={}",
                    gameRoomId, e);
        }
    }

    private void cleanupRttState(Long gameRoomId) {
        try {
            gameRttMeasurementStore.cleanup(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup RTT state during game start failure cleanup. gameRoomId={}", gameRoomId, e);
        }
    }

    private void cleanupWaitingState(Long gameRoomId) {
        try {
            gameWaitingStore.cleanup(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup waiting state during game start failure cleanup. gameRoomId={}", gameRoomId, e);
        }
    }

    private void cleanupEndDeadline(Long gameRoomId) {
        try {
            gameEndScheduleService.cleanupEndDeadline(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to cleanup game end deadline during game start failure cleanup. gameRoomId={}",
                    gameRoomId, e);
        }
    }
}
