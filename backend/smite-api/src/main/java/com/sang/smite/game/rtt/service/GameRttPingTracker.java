package com.sang.smite.game.rtt.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
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

    public synchronized void cleanup(Long gameRoomId) {
        sentAtNanosByRoom.remove(gameRoomId);
    }
}
