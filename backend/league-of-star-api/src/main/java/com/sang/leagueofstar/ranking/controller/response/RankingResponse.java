package com.sang.leagueofstar.ranking.controller.response;

import java.util.List;

public record RankingResponse(
        RankingSummaryResponse summary,
        List<RankingEntryResponse> entries,
        RankingEntryResponse currentUser
) {
}
