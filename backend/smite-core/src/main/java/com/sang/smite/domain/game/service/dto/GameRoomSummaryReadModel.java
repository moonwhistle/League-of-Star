package com.sang.smite.domain.game.service.dto;

import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameStatus;

import java.time.LocalDateTime;
import java.util.List;

public record GameRoomSummaryReadModel(
        Long gameRoomId,
        GameStatus status,
        GameResult result,
        Long winnerId,
        LocalDateTime finishedAt,
        List<Long> participantUserIds
) {

    public GameRoomSummaryReadModel {
        participantUserIds = List.copyOf(participantUserIds);
    }
}
