package com.sang.leagueofstar.domain.game.event;

import java.util.UUID;

public record GameFinishedEvent(
        String eventId,
        Long gameRoomId
) {

    public static GameFinishedEvent create(Long gameRoomId) {
        return new GameFinishedEvent(UUID.randomUUID().toString(), gameRoomId);
    }
}
