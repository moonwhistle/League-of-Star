package com.sang.leagueofstar.notification.match.provider;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.notification.match.dto.MatchResponseResultNotification;
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
        String nickname = findNickname(opponentUserId);

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
                    rankInfo.getRank().name(),
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

    private String findNickname(Long userId) {
        try {
            return userReadService.findById(userId).getNickname();
        } catch (Exception e) {
            log.warn("Failed to lookup opponent user info for match response result: userId={}", userId, e);
            return UNKNOWN_NICKNAME;
        }
    }
}
