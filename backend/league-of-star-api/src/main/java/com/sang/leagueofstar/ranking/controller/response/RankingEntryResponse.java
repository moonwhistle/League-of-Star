package com.sang.leagueofstar.ranking.controller.response;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.user.domain.User;

public record RankingEntryResponse(
        int rankPosition,
        Long userId,
        String nickname,
        Tier tier,
        Division division,
        String rank,
        int lp,
        int tierScore,
        int wins,
        int losses,
        int draws,
        boolean isCurrentUser
) {

    public static RankingEntryResponse from(
            UserRankInfo rankInfo,
            User user,
            int rankPosition,
            boolean isCurrentUser
    ) {
        Rank rank = rankInfo.getRank();

        return new RankingEntryResponse(
                rankPosition,
                rankInfo.getUserId(),
                user.getNickname(),
                rank.tier(),
                rank.division(),
                rankName(rank),
                rankInfo.getLp(),
                rankInfo.getTierScore(),
                rankInfo.getTotalWins(),
                rankInfo.getTotalLosses(),
                rankInfo.getTotalDraws(),
                isCurrentUser
        );
    }

    private static String rankName(Rank rank) {
        if (rank.division() == null) {
            return rank.tier().name();
        }
        return rank.tier().name() + "_" + rank.division().name();
    }
}
