package com.sang.leagueofstar.domain.rank.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeriesStatus {
    IN_PROGRESS("진행 중"),
    SUCCESS("성공"),
    FAILED("실패");

    private final String description;
}
