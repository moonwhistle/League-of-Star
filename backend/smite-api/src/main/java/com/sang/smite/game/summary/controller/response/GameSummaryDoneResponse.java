package com.sang.smite.game.summary.controller.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sang.smite.domain.game.domain.vo.GameResult;

import java.time.LocalDateTime;

public record GameSummaryDoneResponse(
        GameSummaryStatus summaryStatus,
        Long gameId,
        GameResult gameResult,
        Long winnerUserId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime finishedAt,
        GameSummaryPlayerResponse me,
        GameSummaryPlayerResponse opponent
) implements GameSummaryResponse {

    public static GameSummaryDoneResponse of(
            Long gameId,
            GameResult gameResult,
            Long winnerUserId,
            LocalDateTime finishedAt,
            GameSummaryPlayerResponse me,
            GameSummaryPlayerResponse opponent
    ) {
        return new GameSummaryDoneResponse(
                GameSummaryStatus.DONE,
                gameId,
                gameResult,
                winnerUserId,
                finishedAt,
                me,
                opponent
        );
    }
}
