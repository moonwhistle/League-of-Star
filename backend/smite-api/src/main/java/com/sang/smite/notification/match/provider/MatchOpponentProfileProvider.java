package com.sang.smite.notification.match.provider;

import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.service.RankReadService;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.service.UserReadService;
import com.sang.smite.notification.match.dto.MatchResponseResultNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * match_response_result payload에 포함할 상대 유저 프로필을 조회합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchOpponentProfileProvider {

    private static final String UNKNOWN_NICKNAME = "unknown";
    private static final String UNKNOWN_TIER = "UNKNOWN";
    private static final String UNRANKED_TIER = "Unranked";

    private final UserReadService userReadService;
    private final RankReadService rankReadService;

    public MatchResponseResultNotification.Opponent getOpponent(Long opponentUserId, int fallbackTierScore) {
        String nickname = userReadService.findById(opponentUserId)
                .map(User::getNickname)
                .orElse(UNKNOWN_NICKNAME);

        try {
            UserRankInfo rankInfo = rankReadService.getUserRankInfo(opponentUserId);
            if (rankReadService.isPlacementInProgress(opponentUserId)) {
                return new MatchResponseResultNotification.Opponent(
                        opponentUserId,
                        nickname,
                        UNRANKED_TIER,
                        fallbackTierScore
                );
            }
            return new MatchResponseResultNotification.Opponent(
                    opponentUserId,
                    nickname,
                    tierName(rankInfo.getRank()),
                    rankInfo.getTierScore()
            );
        } catch (Exception e) {
            log.warn("Failed to lookup opponent rank info for match response result: userId={}", opponentUserId, e);
            return new MatchResponseResultNotification.Opponent(
                    opponentUserId,
                    nickname,
                    UNKNOWN_TIER,
                    fallbackTierScore
            );
        }
    }

    private String tierName(Rank rank) {
        if (rank.division() == null) {
            return rank.tier().name();
        }
        return rank.tier().name() + "_" + rank.division().name();
    }
}
