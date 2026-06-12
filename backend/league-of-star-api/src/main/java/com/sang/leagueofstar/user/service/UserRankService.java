package com.sang.leagueofstar.user.service;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.user.controller.response.UserRankResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRankService {

    private final UserReadService userReadService;
    private final RankReadService rankReadService;

    public UserRankResponse getMyRank(Long userId) {
        userReadService.findById(userId);
        UserRankInfo rankInfo = rankReadService.getUserRankInfo(userId);
        return UserRankResponse.from(rankInfo);
    }
}
