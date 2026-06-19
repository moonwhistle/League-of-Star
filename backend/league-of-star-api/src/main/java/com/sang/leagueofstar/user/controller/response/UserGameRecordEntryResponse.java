package com.sang.leagueofstar.user.controller.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.domain.user.domain.User;

import java.time.LocalDateTime;

public record UserGameRecordEntryResponse(
        Long gameId,
        GameRecordResult result,
        Long opponentUserId,
        String opponentNickname,
        String rankBefore,
        String rankAfter,
        int lpBefore,
        int lpAfter,
        int lpChange,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime playedAt
) {

    public static UserGameRecordEntryResponse from(GameRecord record, User opponent) {
        return new UserGameRecordEntryResponse(
                record.getGameRoomId(),
                record.getResult(),
                record.getOpponentId(),
                opponent.getNickname(),
                record.getRankBefore().name(),
                record.getRankAfter().name(),
                record.getLpBefore(),
                record.getLpAfter(),
                record.getLpChange(),
                record.getCreatedAt()
        );
    }
}
