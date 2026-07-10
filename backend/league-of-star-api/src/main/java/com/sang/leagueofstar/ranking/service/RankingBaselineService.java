package com.sang.leagueofstar.ranking.service;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.ranking.controller.response.RankingEntryResponse;
import com.sang.leagueofstar.ranking.controller.response.RankingResponse;
import com.sang.leagueofstar.ranking.controller.response.RankingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Measurement-only baseline for ranking troubleshooting.
 * This intentionally performs row-by-row user lookup to reproduce application-level N+1.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingBaselineService {

    private static final int PERCENT_SCALE = 100;

    private final UserReadService userReadService;
    private final RankReadService rankReadService;

    public RankingResponse getRankings(Long userId, int limit) {
        User currentUser = userReadService.findById(userId);
        UserRankInfo currentRankInfo = rankReadService.getUserRankInfo(userId);
        List<UserRankInfo> topRankings = rankReadService.findTopRankings(limit);
        long totalRankers = rankReadService.countRankers();
        int myRankPosition = rankReadService.getRankPosition(currentRankInfo);

        List<RankingEntryResponse> entries = toEntriesWithNPlusOne(topRankings, userId);
        RankingEntryResponse currentUserEntry = RankingEntryResponse.from(
                currentRankInfo,
                currentUser,
                myRankPosition,
                true
        );
        RankingSummaryResponse summary = new RankingSummaryResponse(
                myRankPosition,
                topPercent(myRankPosition, totalRankers),
                totalRankers
        );

        return new RankingResponse(summary, entries, currentUserEntry);
    }

    private List<RankingEntryResponse> toEntriesWithNPlusOne(List<UserRankInfo> topRankings, Long currentUserId) {
        return IntStream.range(0, topRankings.size())
                .mapToObj(index -> {
                    UserRankInfo rankInfo = topRankings.get(index);
                    User ranker = userReadService.findById(rankInfo.getUserId());
                    return RankingEntryResponse.from(
                            rankInfo,
                            ranker,
                            index + 1,
                            rankInfo.getUserId().equals(currentUserId)
                    );
                })
                .toList();
    }

    private int topPercent(int rankPosition, long totalRankers) {
        if (totalRankers <= 0) {
            return PERCENT_SCALE;
        }

        int percent = (int) Math.ceil((rankPosition * PERCENT_SCALE) / (double) totalRankers);
        return Math.max(1, Math.min(PERCENT_SCALE, percent));
    }
}
