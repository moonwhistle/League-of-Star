package com.sang.leagueofstar.domain.rank.service.dto;

import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordSeriesType;

/**
 * rank 반영 전후 snapshot으로 gameRecord에 저장할 값을 제공합니다.
 */
public record RankRecordSettlementResult(
        Long userId,
        Long rankSeriesId,
        GameRecordSeriesType seriesType,
        int lpBefore,
        int lpAfter,
        Rank rankBefore,
        Rank rankAfter
) {
}
