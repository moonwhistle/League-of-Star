package com.sang.smite.game.rtt.service;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.game.rtt.common.constant.GameRttConstants;
import com.sang.smite.game.rtt.domain.GameRttFailureReason;
import com.sang.smite.game.rtt.domain.GameRttPendingPing;
import com.sang.smite.game.rtt.domain.GameRttPongResult;
import com.sang.smite.game.rtt.domain.GameRttStartReadyState;
import com.sang.smite.game.rtt.repository.GameRttMeasurementStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GameRttMeasurementService {

    private final GameRttMeasurementStore gameRttMeasurementStore;
    private final GameRttPingTracker gameRttPingTracker;
    private final GameRttFailureProcessor gameRttFailureProcessor;

    public boolean startMeasurement(Long gameRoomId, List<Long> userIds) {
        List<Long> sortedUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (sortedUserIds.size() != GameRoom.MAX_PARTICIPANTS) {
            return false;
        }

        return gameRttMeasurementStore.initializeIfAbsent(
                gameRoomId,
                sortedUserIds.get(0),
                sortedUserIds.get(1)
        );
    }

    public void recordPingSent(Long gameRoomId, Long userId, int seq) {
        gameRttPingTracker.recordSentAt(gameRoomId, userId, seq, System.nanoTime());
    }

    public GameRttPongResult recordPong(Long gameRoomId, Long userId, int seq) {
        return recordPong(gameRoomId, userId, seq, System.nanoTime());
    }

    public boolean failMeasurement(Long gameRoomId, Long userId) {
        boolean failed = gameRttMeasurementStore.markFailed(gameRoomId, userId);
        gameRttFailureProcessor.processFailureWithLock(gameRoomId, GameRttFailureReason.RTT_FAILED);
        return failed;
    }

    public Optional<GameRttStartReadyState> findStartReadyState(Long gameRoomId) {
        return gameRttMeasurementStore.findStartReadyState(gameRoomId);
    }

    public int failTimedOutPings() {
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(GameRttConstants.RTT_PING_TIMEOUT_MILLIS);
        return failTimedOutPings(System.nanoTime(), timeoutNanos);
    }

    int failTimedOutPings(long nowNanos, long timeoutNanos) {
        List<GameRttPendingPing> timedOutPings = gameRttPingTracker.consumeTimedOutSentAts(nowNanos, timeoutNanos);
        int failedCount = 0;
        for (GameRttPendingPing timedOutPing : timedOutPings) {
            try {
                if (failMeasurement(timedOutPing.gameRoomId(), timedOutPing.userId())) {
                    failedCount++;
                }
            } catch (RuntimeException e) {
                gameRttPingTracker.recordSentAt(
                        timedOutPing.gameRoomId(),
                        timedOutPing.userId(),
                        timedOutPing.seq(),
                        timedOutPing.sentAtNanos()
                );
                throw e;
            }
        }
        return failedCount;
    }

    GameRttPongResult recordPong(Long gameRoomId, Long userId, int seq, long receivedAtNanos) {
        OptionalLong sentAtNanos = gameRttPingTracker.consumeSentAt(gameRoomId, userId, seq);
        if (sentAtNanos.isEmpty()) {
            return GameRttPongResult.rejected();
        }

        long elapsedNanos = Math.max(0, receivedAtNanos - sentAtNanos.getAsLong());
        long rttMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        GameRttPongResult pongResult = gameRttMeasurementStore.appendSample(gameRoomId, userId, rttMillis);
        if (pongResult.completed() && !pongResult.passed()) {
            gameRttFailureProcessor.processFailureWithLock(gameRoomId, GameRttFailureReason.RTT_TOO_HIGH);
        }
        return pongResult;
    }
}
