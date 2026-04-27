package com.sang.smite.domain.rank.service;

import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.repository.UserRankInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RankCommandService {

    private final UserRankInfoRepository userRankInfoRepository;

    /**
     * 유저의 초기 랭크 정보를 생성합니다.
     * [정책 3.2] 신규 유저는 Iron IV, 0 LP, 배치 고사 상태로 시작합니다.
     *
     * @param userId 생성된 유저의 ID
     */
    public void initializeRank(Long userId) {
        UserRankInfo rankInfo = UserRankInfo.builder()
                .userId(userId)
                .build();
        userRankInfoRepository.save(rankInfo);
    }
}
