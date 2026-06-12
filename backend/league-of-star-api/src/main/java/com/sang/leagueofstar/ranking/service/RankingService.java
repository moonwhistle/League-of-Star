package com.sang.leagueofstar.ranking.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int PERCENT_SCALE = 100;

    private final UserReadService userReadService;
    private final RankReadService rankReadService;

    public RankingResponse getRankings(Long userId, int limit) {
        User currentUser = userReadService.findById(userId);
        UserRankInfo currentRankInfo = rankReadService.getUserRankInfo(userId);
        List<UserRankInfo> topRankings = rankReadService.findTopRankings(limit);
        long totalRankers = rankReadService.countRankers();
        int myRankPosition = rankReadService.getRankPosition(currentRankInfo);

        Map<Long, User> usersById = findUsersByRankings(topRankings, currentUser);

        List<RankingEntryResponse> entries = toEntries(topRankings, usersById, userId);
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

    private Map<Long, User> findUsersByRankings(List<UserRankInfo> topRankings, User currentUser) {
        Set<Long> userIds = topRankings.stream()
                .map(UserRankInfo::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        userIds.add(currentUser.getId());

        Map<Long, User> usersById = userReadService.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        usersById.putIfAbsent(currentUser.getId(), currentUser);
        return usersById;
    }

    private List<RankingEntryResponse> toEntries(
            List<UserRankInfo> topRankings,
            Map<Long, User> usersById,
            Long currentUserId
    ) {
        return IntStream.range(0, topRankings.size())
                .mapToObj(index -> {
                    UserRankInfo rankInfo = topRankings.get(index);
                    return RankingEntryResponse.from(
                        rankInfo,
                        resolveUser(usersById, rankInfo.getUserId()),
                        index + 1,
                        rankInfo.getUserId().equals(currentUserId)
                    );
                })
                .toList();
    }

    private User resolveUser(Map<Long, User> usersById, Long userId) {
        User user = usersById.get(userId);
        if (user == null) {
            throw new CoreException(CoreErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private int topPercent(int rankPosition, long totalRankers) {
        if (totalRankers <= 0) {
            return PERCENT_SCALE;
        }

        int percent = (int) Math.ceil((rankPosition * PERCENT_SCALE) / (double) totalRankers);
        return Math.max(1, Math.min(PERCENT_SCALE, percent));
    }
}
