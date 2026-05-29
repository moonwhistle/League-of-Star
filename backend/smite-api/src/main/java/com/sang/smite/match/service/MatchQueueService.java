package com.sang.smite.match.service;

import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.service.RankReadService;
import com.sang.smite.matching.command.MatchQueueCommandService;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 매칭 진입/취소 흐름을 조율하는 api 레이어 서비스입니다.
 *
 * <p>smite-matching 모듈의 {@link MatchQueueCommandService}를 호출하기 전에
 * 유저의 진행 중 gameRoom 여부와 티어 점수를 조회하는 책임을 담당합니다.
 * JPA(smite-core)와 Redis 도메인 로직(smite-matching)의 경계를 이 클래스가 맡습니다.
 */
@Service
@RequiredArgsConstructor
public class MatchQueueService {

    private static final int PLACEMENT_MATCHING_TIER_SCORE = 9;

    private final MatchQueueCommandService matchService;
    private final RankReadService rankReadService;
    private final GameRoomReadService gameRoomReadService;

    /**
     * 유저를 매칭 대기열에 진입시킵니다.
     * 진행 중인 gameRoom이 없는지 확인하고 현재 랭크를 조회한 후 매칭 모듈에 위임합니다.
     *
     * @param userId 진입 요청한 유저의 ID
     */
    public void joinQueue(Long userId) {
        validateNoActiveGameRoom(userId);
        int tierScore = resolveQueueTierScore(userId);
        matchService.joinQueue(userId, tierScore);
    }

    /**
     * 유저를 매칭 대기열에서 취소(이탈)시킵니다.
     * 유저의 현재 랭크를 조회한 후 매칭 모듈에 위임합니다.
     *
     * @param userId 취소 요청한 유저의 ID
     */
    public void leaveQueue(Long userId) {
        int tierScore = resolveQueueTierScore(userId);
        matchService.leaveQueue(userId, tierScore);
    }

    /**
     * 유저의 랭크 정보를 조회합니다.
     * Core 모듈의 RankReadService가 예외 처리를 담당합니다.
     */
    private UserRankInfo getRankInfo(Long userId) {
        return rankReadService.getUserRankInfo(userId);
    }

    private int resolveQueueTierScore(Long userId) {
        UserRankInfo rankInfo = getRankInfo(userId);
        if (rankReadService.isPlacementInProgress(userId)) {
            return PLACEMENT_MATCHING_TIER_SCORE;
        }
        return rankInfo.getTierScore();
    }

    private void validateNoActiveGameRoom(Long userId) {
        if (gameRoomReadService.existsActiveGameRoomByUserId(userId)) {
            throw new MatchingException(MatchingErrorCode.ACTIVE_GAME_ROOM_EXISTS);
        }
    }
}
