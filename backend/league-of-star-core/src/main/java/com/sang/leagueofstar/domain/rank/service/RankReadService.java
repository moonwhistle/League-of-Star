package com.sang.leagueofstar.domain.rank.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.SeriesStatus;
import com.sang.leagueofstar.domain.rank.domain.vo.SeriesType;
import com.sang.leagueofstar.domain.rank.repository.RankSeriesRepository;
import com.sang.leagueofstar.domain.rank.repository.UserRankInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저의 랭크 정보를 조회하는 읽기 전용 도메인 서비스입니다. (CQRS - Read Side)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankReadService {

    private final UserRankInfoRepository userRankInfoRepository;
    private final RankSeriesRepository rankSeriesRepository;

    /**
     * 유저 ID를 기반으로 랭크 정보를 조회합니다.
     * 정보가 없을 경우 CoreException을 던집니다.
     *
     * @param userId 유저 ID
     * @return 유저의 랭크 정보
     * @throws CoreException 유저의 랭크 정보가 존재하지 않을 경우 (RANK_NOT_FOUND)
     */
    public UserRankInfo getUserRankInfo(Long userId) {
        return userRankInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.RANK_NOT_FOUND));
    }

    /**
     * 진행 중인 배치 시리즈가 있는지 조회합니다.
     */
    public boolean isPlacementInProgress(Long userId) {
        return rankSeriesRepository.existsByUserIdAndStatusAndType(
                userId,
                SeriesStatus.IN_PROGRESS,
                SeriesType.PLACEMENT
        );
    }
}
