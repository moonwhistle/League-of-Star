package com.sang.leagueofstar.game.summary.dto;

public sealed interface GameSummaryResponse permits GameSummaryPendingResponse, GameSummaryDoneResponse {

    GameSummaryStatus summaryStatus();

    Long gameId();
}
