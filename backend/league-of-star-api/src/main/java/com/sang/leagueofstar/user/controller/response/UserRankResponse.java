package com.sang.leagueofstar.user.controller.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;

import java.time.LocalDateTime;

public record UserRankResponse(
        Long userId,
        Tier tier,
        Division division,
        String rank,
        int lp,
        int tierScore,
        int wins,
        int losses,
        int draws,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime rankUpdatedAt
) {

    public static UserRankResponse from(UserRankInfo rankInfo) {
        Rank rank = rankInfo.getRank();

        return new UserRankResponse(
                rankInfo.getUserId(),
                rank.tier(),
                rank.division(),
                rankName(rank),
                rankInfo.getLp(),
                rankInfo.getTierScore(),
                rankInfo.getTotalWins(),
                rankInfo.getTotalLosses(),
                rankInfo.getTotalDraws(),
                rankInfo.getUpdatedAt()
        );
    }

    private static String rankName(Rank rank) {
        if (rank.division() == null) {
            return rank.tier().name();
        }
        return rank.tier().name() + "_" + rank.division().name();
    }
}
