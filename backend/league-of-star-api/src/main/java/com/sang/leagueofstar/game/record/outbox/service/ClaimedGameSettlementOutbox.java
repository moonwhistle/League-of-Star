package com.sang.leagueofstar.game.record.outbox.service;

public record ClaimedGameSettlementOutbox(
        String eventId,
        Long gameRoomId,
        String lockToken,
        int retryCount
) {
}
