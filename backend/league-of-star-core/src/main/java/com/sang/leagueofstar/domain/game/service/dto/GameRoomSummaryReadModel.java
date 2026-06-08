package com.sang.leagueofstar.domain.game.service.dto;

import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;

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
