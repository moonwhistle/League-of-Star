package com.sang.smite.domain.rank.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class RankCommandService {

    private static final int PROMOTION_ENTRY_LP = 100;
    private static final int DEMOTION_LP = 75;

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
     * gameRecord 생성 transaction 안에서 참가자별 누적 전적과 일반 RANK LP를 반영합니다.
     */
    public List<RankRecordSettlementResult> applyRecordResults(List<RankRecordSettlementCommand> commands) {
        Map<Long, UserRankInfo> rankInfos = findRankInfosForUpdate(commands);
        Map<Long, RankSnapshot> beforeSnapshots = rankInfos.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> RankSnapshot.from(entry.getValue())));

        return commands.stream()
                .map(command -> applyRecordResult(command, rankInfos, beforeSnapshots))
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

    private RankRecordSettlementResult applyRecordResult(
            RankRecordSettlementCommand command,
            Map<Long, UserRankInfo> rankInfos,
            Map<Long, RankSnapshot> beforeSnapshots
    ) {
        UserRankInfo rankInfo = rankInfos.get(command.userId());
        RankSnapshot before = beforeSnapshots.get(command.userId());
        RankSnapshot opponentBefore = beforeSnapshots.get(command.opponentId());
        if (rankInfo == null || before == null || opponentBefore == null) {
            throw new CoreException(CoreErrorCode.RANK_NOT_FOUND);
        }

        rankInfo.applyRecordResult(command.result());
        if (command.seriesType() == GameRecordSeriesType.RANK) {
            applyRankLp(rankInfo, command.result(), before, opponentBefore);
        }

        return new RankRecordSettlementResult(
                command.userId(),
                before.lp(),
                rankInfo.getLp(),
                before.rank(),
                rankInfo.getRank()
        );
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
        if (shouldEnterPromotionSeries(before.rank(), nextLp)) {
            rankInfo.updateRankAndLp(before.rank(), PROMOTION_ENTRY_LP);
            rankSeriesRepository.save(RankSeries.createPromotion(rankInfo.getUserId(), nextRank(before.rank())));
            return;
        }

        rankInfo.updateLp(nextLp - before.lp());
    }

    private void applyRankLoss(UserRankInfo rankInfo, RankSnapshot before, RankSnapshot opponentBefore) {
        if (shouldDemote(before)) {
            rankInfo.updateRankAndLp(previousRank(before.rank()), DEMOTION_LP);
            return;
        }

        int lpChange = before.rank().calculateLossLp(opponentBefore.rank());
        rankInfo.updateLp(-lpChange);
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
