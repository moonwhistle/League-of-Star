package com.sang.smite.domain.rank.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.SeriesStatus;
import com.sang.smite.domain.rank.domain.vo.SeriesType;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementCommand;
import com.sang.smite.domain.rank.service.dto.RankRecordSettlementResult;
import com.sang.smite.domain.rank.repository.RankSeriesRepository;
import com.sang.smite.domain.rank.repository.UserRankInfoRepository;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class RankCommandService {

    private static final int PROMOTION_ENTRY_LP = 100;
    private static final int DEMOTION_LP = 75;
    private static final int GRANDMASTER_ENTRY_LP = 200;
    private static final int CHALLENGER_ENTRY_LP = 500;

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

    /**
     * gameRecord 생성 transaction 안에서 참가자별 누적 전적, LP, 진행 중 RankSeries를 반영합니다.
     */
    public List<RankRecordSettlementResult> applyRecordResults(List<RankRecordSettlementCommand> commands) {
        Map<Long, UserRankInfo> rankInfos = findRankInfosForUpdate(commands);
        Map<Long, RankSeries> activeRankSeries = findActiveRankSeriesForUpdate(commands);
        Map<Long, RankSnapshot> beforeSnapshots = rankInfos.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> RankSnapshot.from(entry.getValue())));

        return commands.stream()
                .map(command -> applyRecordResult(command, rankInfos, activeRankSeries, beforeSnapshots))
                .toList();
    }

    private Map<Long, UserRankInfo> findRankInfosForUpdate(List<RankRecordSettlementCommand> commands) {
        return commands.stream()
                .flatMap(command -> Stream.of(command.userId(), command.opponentId()))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(this::findRankInfoForUpdate)
                .collect(Collectors.toMap(UserRankInfo::getUserId, Function.identity()));
    }

    private UserRankInfo findRankInfoForUpdate(Long userId) {
        return userRankInfoRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.RANK_NOT_FOUND));
    }

    private Map<Long, RankSeries> findActiveRankSeriesForUpdate(List<RankRecordSettlementCommand> commands) {
        return commands.stream()
                .map(RankRecordSettlementCommand::userId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(userId -> rankSeriesRepository.findByUserIdAndStatus(userId, SeriesStatus.IN_PROGRESS))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(RankSeries::getUserId, Function.identity()));
    }

    private RankRecordSettlementResult applyRecordResult(
            RankRecordSettlementCommand command,
            Map<Long, UserRankInfo> rankInfos,
            Map<Long, RankSeries> activeRankSeries,
            Map<Long, RankSnapshot> beforeSnapshots
    ) {
        UserRankInfo rankInfo = rankInfos.get(command.userId());
        RankSnapshot before = beforeSnapshots.get(command.userId());
        RankSnapshot opponentBefore = beforeSnapshots.get(command.opponentId());
        if (rankInfo == null || before == null || opponentBefore == null) {
            throw new CoreException(CoreErrorCode.RANK_NOT_FOUND);
        }

        rankInfo.applyRecordResult(command.result());
        RankSeries rankSeries = activeRankSeries.get(command.userId());
        if (rankSeries == null) {
            applyRankLp(rankInfo, command.result(), before, opponentBefore);
        } else {
            applyRankSeriesResult(rankInfo, rankSeries, command.result(), before);
        }

        return new RankRecordSettlementResult(
                command.userId(),
                rankSeries == null ? null : rankSeries.getId(),
                rankSeries == null ? GameRecordSeriesType.RANK : toRecordSeriesType(rankSeries.getType()),
                before.lp(),
                rankInfo.getLp(),
                before.rank(),
                rankInfo.getRank()
        );
    }

    private void applyRankSeriesResult(
            UserRankInfo rankInfo,
            RankSeries rankSeries,
            GameRecordResult result,
            RankSnapshot before
    ) {
        applySeriesCount(rankSeries, result);
        if (rankSeries.getType() == SeriesType.PLACEMENT) {
            applyPlacementCompletion(rankInfo, rankSeries);
            return;
        }
        applyPromotionCompletion(rankInfo, rankSeries, before);
    }

    private void applySeriesCount(RankSeries rankSeries, GameRecordResult result) {
        switch (result) {
            case WIN -> rankSeries.addWin();
            case LOSS -> rankSeries.addLoss();
            case DRAW -> rankSeries.addDraw();
        }
    }

    private void applyPlacementCompletion(UserRankInfo rankInfo, RankSeries rankSeries) {
        if (rankSeries.getStatus() != SeriesStatus.SUCCESS) {
            return;
        }
        rankInfo.updateRankAndLp(resolvePlacementRank(rankSeries.getWins()), 0);
    }

    private Rank resolvePlacementRank(int wins) {
        if (wins >= 9) {
            return Rank.of(Tier.PLATINUM, Division.IV);
        }
        if (wins >= 7) {
            return Rank.of(Tier.GOLD, Division.IV);
        }
        if (wins >= 5) {
            return Rank.of(Tier.SILVER, Division.IV);
        }
        if (wins >= 3) {
            return Rank.of(Tier.BRONZE, Division.IV);
        }
        return Rank.of(Tier.IRON, Division.IV);
    }

    private void applyPromotionCompletion(UserRankInfo rankInfo, RankSeries rankSeries, RankSnapshot before) {
        if (rankSeries.getStatus() == SeriesStatus.SUCCESS) {
            if (rankSeries.getTargetRank() == null) {
                throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
            }
            rankInfo.updateRankAndLp(rankSeries.getTargetRank(), 0);
        } else if (rankSeries.getStatus() == SeriesStatus.FAILED) {
            rankInfo.updateRankAndLp(before.rank(), DEMOTION_LP);
        }
    }

    private GameRecordSeriesType toRecordSeriesType(SeriesType seriesType) {
        return switch (seriesType) {
            case PLACEMENT -> GameRecordSeriesType.PLACEMENT;
            case PROMOTION -> GameRecordSeriesType.PROMOTION;
        };
    }

    private void applyRankLp(
            UserRankInfo rankInfo,
            GameRecordResult result,
            RankSnapshot before,
            RankSnapshot opponentBefore
    ) {
        switch (result) {
            case WIN -> applyRankWin(rankInfo, before, opponentBefore);
            case LOSS -> applyRankLoss(rankInfo, before, opponentBefore);
            case DRAW -> {
                // DRAW는 누적 전적만 반영하고 LP/rank는 변경하지 않습니다.
            }
        }
    }

    private void applyRankWin(UserRankInfo rankInfo, RankSnapshot before, RankSnapshot opponentBefore) {
        int nextLp = before.lp() + before.rank().calculateWinLp(opponentBefore.rank());
        if (isApexRank(before.rank())) {
            applyApexRankWin(rankInfo, before.rank(), nextLp);
            return;
        }
        if (shouldEnterPromotionSeries(before.rank(), nextLp)) {
            rankInfo.updateRankAndLp(before.rank(), PROMOTION_ENTRY_LP);
            rankSeriesRepository.save(RankSeries.createPromotion(rankInfo.getUserId(), nextRank(before.rank())));
            return;
        }

        rankInfo.updateLp(nextLp - before.lp());
    }

    private void applyRankLoss(UserRankInfo rankInfo, RankSnapshot before, RankSnapshot opponentBefore) {
        if (isApexRank(before.rank())) {
            applyApexRankLoss(rankInfo, before, opponentBefore);
            return;
        }
        if (shouldDemote(before)) {
            rankInfo.updateRankAndLp(previousRank(before.rank()), DEMOTION_LP);
            return;
        }

        int lpChange = before.rank().calculateLossLp(opponentBefore.rank());
        rankInfo.updateLp(-lpChange);
    }

    private void applyApexRankWin(UserRankInfo rankInfo, Rank beforeRank, int nextLp) {
        rankInfo.updateRankAndLp(resolveApexRankAfterWin(beforeRank, nextLp), nextLp);
    }

    private Rank resolveApexRankAfterWin(Rank beforeRank, int nextLp) {
        return switch (beforeRank.tier()) {
            case MASTER -> {
                if (nextLp >= GRANDMASTER_ENTRY_LP) {
                    yield apexRank(Tier.GRANDMASTER);
                }
                yield apexRank(Tier.MASTER);
            }
            case GRANDMASTER -> {
                if (nextLp >= CHALLENGER_ENTRY_LP) {
                    yield apexRank(Tier.CHALLENGER);
                }
                yield apexRank(Tier.GRANDMASTER);
            }
            case CHALLENGER -> apexRank(Tier.CHALLENGER);
            default -> beforeRank;
        };
    }

    private void applyApexRankLoss(UserRankInfo rankInfo, RankSnapshot before, RankSnapshot opponentBefore) {
        if (before.rank().tier() == Tier.MASTER && before.lp() == 0) {
            rankInfo.updateRankAndLp(Rank.of(Tier.DIAMOND, Division.I), DEMOTION_LP);
            return;
        }

        int lpChange = before.rank().calculateLossLp(opponentBefore.rank());
        int nextLp = Math.max(before.lp() - lpChange, 0);
        rankInfo.updateRankAndLp(resolveApexRankAfterLoss(before.rank(), nextLp), nextLp);
    }

    private Rank resolveApexRankAfterLoss(Rank beforeRank, int nextLp) {
        return switch (beforeRank.tier()) {
            case MASTER -> apexRank(Tier.MASTER);
            case GRANDMASTER -> {
                if (nextLp < GRANDMASTER_ENTRY_LP) {
                    yield apexRank(Tier.MASTER);
                }
                yield apexRank(Tier.GRANDMASTER);
            }
            case CHALLENGER -> {
                if (nextLp < CHALLENGER_ENTRY_LP) {
                    yield apexRank(Tier.GRANDMASTER);
                }
                yield apexRank(Tier.CHALLENGER);
            }
            default -> beforeRank;
        };
    }

    private boolean isApexRank(Rank rank) {
        return rank.tier().getLevel() >= Tier.MASTER.getLevel();
    }

    private Rank apexRank(Tier tier) {
        return Rank.of(tier, null);
    }

    private boolean shouldEnterPromotionSeries(Rank rank, int nextLp) {
        return rank.tier().getLevel() < Tier.MASTER.getLevel()
                && nextLp >= 99;
    }

    private boolean shouldDemote(RankSnapshot before) {
        return before.lp() == 0 && previousRank(before.rank()).compareTo(before.rank()) < 0;
    }

    private Rank nextRank(Rank rank) {
        if (rank.division() == null) {
            return rank;
        }
        if (rank.division() == Division.I) {
            Tier nextTier = nextTier(rank);
            return Rank.of(nextTier, nextTier == Tier.MASTER ? null : Division.IV);
        }
        return Rank.of(rank.tier(), previousDivision(rank.division()));
    }

    private Rank previousRank(Rank rank) {
        if (rank.division() == null) {
            return rank;
        }
        if (rank.tier() == Tier.IRON && rank.division() == Division.IV) {
            return rank;
        }
        if (rank.division() == Division.IV) {
            return Rank.of(previousTier(rank), Division.I);
        }
        return Rank.of(rank.tier(), nextDivision(rank.division()));
    }

    private Tier nextTier(Rank rank) {
        int nextLevel = rank.tier().getLevel() + 1;
        for (Tier tier : Tier.values()) {
            if (tier.getLevel() == nextLevel) {
                return tier;
            }
        }
        return rank.tier();
    }

    private Tier previousTier(Rank rank) {
        int previousLevel = rank.tier().getLevel() - 1;
        for (Tier tier : Tier.values()) {
            if (tier.getLevel() == previousLevel) {
                return tier;
            }
        }
        return rank.tier();
    }

    private Division previousDivision(Division division) {
        return Division.values()[division.ordinal() - 1];
    }

    private Division nextDivision(Division division) {
        return Division.values()[division.ordinal() + 1];
    }

    private record RankSnapshot(
            Rank rank,
            int lp
    ) {
        private static RankSnapshot from(UserRankInfo rankInfo) {
            return new RankSnapshot(rankInfo.getRank(), rankInfo.getLp());
        }
    }
}
