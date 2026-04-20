package com.sang.smite.domain.rank.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeriesStatus {
    IN_PROGRESS("진행 중"),
    PROMOTED("승급 성공"),
    FAILED("승급 실패");

    private final String description;
}
