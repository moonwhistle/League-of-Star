package com.sang.smite.domain.rank.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeriesType {
    PLACEMENT("배치 고사"),
    PROMOTION("승급전");

    private final String description;
}
