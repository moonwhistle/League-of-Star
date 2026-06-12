package com.sang.leagueofstar.ranking.controller.response;

public record RankingSummaryResponse(
        int myRankPosition,
        int topPercent,
        long totalRankers
) {
}
