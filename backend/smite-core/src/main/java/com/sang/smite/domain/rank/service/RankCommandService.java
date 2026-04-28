package com.sang.smite.domain.rank.service;

import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.repository.RankSeriesRepository;
import com.sang.smite.domain.rank.repository.UserRankInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RankCommandService {

    private final UserRankInfoRepository userRankInfoRepository;
    private final RankSeriesRepository rankSeriesRepository;

    /**
     * 유저의 초기 랭크 정보를 생성하고 배치 고사를 시작합니다.
     * [정책 4.4] 신규 유저는 10판의 배치 게임을 수행합니다.
     *
     * @param userId 생성된 유저의 ID
     */
    public void initializeRank(Long userId) {
        // 1. 초기 랭크 정보 생성 (Iron IV, 0 LP)
        UserRankInfo rankInfo = UserRankInfo.builder()
                .userId(userId)
                .build();
        userRankInfoRepository.save(rankInfo);

        // 2. 배치 고사 시리즈 시작
        RankSeries placementSeries = RankSeries.createPlacement(userId);
        rankSeriesRepository.save(placementSeries);
    }
}
