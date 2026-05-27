package com.sang.smite.domain.rank.service.dto;

import com.sang.smite.domain.record.domain.vo.GameRecordResult;

/**
 * gameRecord 생성 전에 rank 도메인에 적용할 참가자별 결과 명령입니다.
 */
public record RankRecordSettlementCommand(
        Long userId,
        Long opponentId,
        GameRecordResult result
) {
}
