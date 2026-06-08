package com.sang.leagueofstar.game.summary.dto;

public record GameSummaryPendingResponse(
        GameSummaryStatus summaryStatus,
        Long gameId,
        long retryAfterMillis
) implements GameSummaryResponse {

    public static GameSummaryPendingResponse of(Long gameId, long retryAfterMillis) {
        return new GameSummaryPendingResponse(GameSummaryStatus.PENDING, gameId, retryAfterMillis);
    }
}
