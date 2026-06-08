package com.sang.leagueofstar.game.rtt.service;

import com.sang.leagueofstar.game.rtt.domain.GameRttPendingPing;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

@Component
public class GameRttPingTracker {

    private final Map<Long, Map<Long, Map<Integer, Long>>> sentAtNanosByRoom = new HashMap<>();

    public synchronized void recordSentAt(Long gameRoomId, Long userId, int seq, long sentAtNanos) {
        sentAtNanosByRoom.computeIfAbsent(gameRoomId, ignored -> new HashMap<>())
                .computeIfAbsent(userId, ignored -> new HashMap<>())
                .put(seq, sentAtNanos);
    }

    public synchronized OptionalLong findSentAt(Long gameRoomId, Long userId, int seq) {
        Map<Long, Map<Integer, Long>> roomSentAt = sentAtNanosByRoom.get(gameRoomId);
        if (roomSentAt == null) {
            return OptionalLong.empty();
        }

        Map<Integer, Long> userSentAt = roomSentAt.get(userId);
        if (userSentAt == null || !userSentAt.containsKey(seq)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(userSentAt.get(seq));
    }

    public synchronized OptionalLong consumeSentAt(Long gameRoomId, Long userId, int seq) {
        Map<Long, Map<Integer, Long>> roomSentAt = sentAtNanosByRoom.get(gameRoomId);
        if (roomSentAt == null) {
            return OptionalLong.empty();
        }

        Map<Integer, Long> userSentAt = roomSentAt.get(userId);
        if (userSentAt == null || !userSentAt.containsKey(seq)) {
            return OptionalLong.empty();
        }

        long sentAtNanos = userSentAt.remove(seq);
        if (userSentAt.isEmpty()) {
            roomSentAt.remove(userId);
        }
        if (roomSentAt.isEmpty()) {
            sentAtNanosByRoom.remove(gameRoomId);
        }
        return OptionalLong.of(sentAtNanos);
    }

    public synchronized List<GameRttPendingPing> consumeTimedOutSentAts(long nowNanos, long timeoutNanos) {
        List<GameRttPendingPing> timedOutPings = new ArrayList<>();
        Iterator<Map.Entry<Long, Map<Long, Map<Integer, Long>>>> roomIterator = sentAtNanosByRoom.entrySet().iterator();
        while (roomIterator.hasNext()) {
            Map.Entry<Long, Map<Long, Map<Integer, Long>>> roomEntry = roomIterator.next();
            consumeTimedOutRoomPings(roomEntry.getKey(), roomEntry.getValue(), nowNanos, timeoutNanos, timedOutPings);
            if (roomEntry.getValue().isEmpty()) {
                roomIterator.remove();
            }
        }
        return timedOutPings;
    }

    public synchronized void cleanup(Long gameRoomId) {
        sentAtNanosByRoom.remove(gameRoomId);
    }

    private void consumeTimedOutRoomPings(Long gameRoomId,
                                          Map<Long, Map<Integer, Long>> roomSentAt,
                                          long nowNanos,
                                          long timeoutNanos,
                                          List<GameRttPendingPing> timedOutPings) {
        Iterator<Map.Entry<Long, Map<Integer, Long>>> userIterator = roomSentAt.entrySet().iterator();
        while (userIterator.hasNext()) {
            Map.Entry<Long, Map<Integer, Long>> userEntry = userIterator.next();
            consumeTimedOutUserPings(gameRoomId, userEntry.getKey(), userEntry.getValue(),
                    nowNanos, timeoutNanos, timedOutPings);
            if (userEntry.getValue().isEmpty()) {
                userIterator.remove();
            }
        }
    }

    private void consumeTimedOutUserPings(Long gameRoomId,
                                          Long userId,
                                          Map<Integer, Long> userSentAt,
                                          long nowNanos,
                                          long timeoutNanos,
                                          List<GameRttPendingPing> timedOutPings) {
        Iterator<Map.Entry<Integer, Long>> seqIterator = userSentAt.entrySet().iterator();
        while (seqIterator.hasNext()) {
            Map.Entry<Integer, Long> seqEntry = seqIterator.next();
            long sentAtNanos = seqEntry.getValue();
            if (nowNanos - sentAtNanos < timeoutNanos) {
                continue;
            }
            timedOutPings.add(new GameRttPendingPing(gameRoomId, userId, seqEntry.getKey(), sentAtNanos));
            seqIterator.remove();
        }
    }
}
