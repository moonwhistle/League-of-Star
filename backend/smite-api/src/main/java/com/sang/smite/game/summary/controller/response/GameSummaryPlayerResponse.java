package com.sang.smite.game.summary.controller.response;

import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;

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
