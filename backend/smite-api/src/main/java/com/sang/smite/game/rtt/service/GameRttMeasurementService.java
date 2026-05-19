package com.sang.smite.game.rtt.service;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.game.rtt.domain.GameRttPongResult;
import com.sang.smite.game.rtt.repository.GameRttMeasurementStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GameRttMeasurementService {

    private final GameRttMeasurementStore gameRttMeasurementStore;
    private final GameRttPingTracker gameRttPingTracker;

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

    GameRttPongResult recordPong(Long gameRoomId, Long userId, int seq, long receivedAtNanos) {
        OptionalLong sentAtNanos = gameRttPingTracker.consumeSentAt(gameRoomId, userId, seq);
        if (sentAtNanos.isEmpty()) {
            return GameRttPongResult.rejected();
        }

        long elapsedNanos = Math.max(0, receivedAtNanos - sentAtNanos.getAsLong());
        long rttMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        return gameRttMeasurementStore.appendSample(gameRoomId, userId, rttMillis);
    }
}
