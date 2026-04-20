package com.sang.smite.domain.rank.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeriesResult {
    WIN("승리"),
    LOSS("패배"),
    DRAW("무승부"),
    PENDING("대기 중");

    private final String description;
}
