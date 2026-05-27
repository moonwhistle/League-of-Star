package com.sang.smite.game.summary.controller.response;

public sealed interface GameSummaryResponse permits GameSummaryPendingResponse, GameSummaryDoneResponse {

    GameSummaryStatus summaryStatus();

    Long gameId();
}
