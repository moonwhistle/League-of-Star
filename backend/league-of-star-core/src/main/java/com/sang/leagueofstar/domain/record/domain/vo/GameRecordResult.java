package com.sang.leagueofstar.domain.record.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameRecordResult {
    WIN("승리"),
    LOSS("패배"),
    DRAW("무승부");

    private final String description;
}
