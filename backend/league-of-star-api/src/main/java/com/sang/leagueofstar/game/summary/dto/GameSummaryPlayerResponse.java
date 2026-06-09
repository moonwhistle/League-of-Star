package com.sang.leagueofstar.game.summary.dto;

import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordSeriesType;

public record GameSummaryPlayerResponse(
        Long userId,
        String nickname,
        GameRecordResult result,
        int lpBefore,
        int lpAfter,
        int lpChange,
        String rankBefore,
        String rankAfter,
        GameRecordSeriesType seriesType,
        Long rankSeriesId
) {
}
