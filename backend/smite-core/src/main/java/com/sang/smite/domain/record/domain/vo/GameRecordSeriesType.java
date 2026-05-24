package com.sang.smite.domain.record.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * gameRecord가 일반 랭크, 배치, 승급전 중 어느 흐름에서 생성됐는지 표현합니다.
 */
@Getter
@RequiredArgsConstructor
public enum GameRecordSeriesType {
    RANK("일반 랭크"),
    PLACEMENT("배치 고사"),
    PROMOTION("승급전");

    private final String description;
}
