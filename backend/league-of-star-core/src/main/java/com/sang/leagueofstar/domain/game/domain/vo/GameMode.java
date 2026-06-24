package com.sang.leagueofstar.domain.game.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameMode {

    MATCH("랭크 매치"),
    PRACTICE("연습 모드"),
    CUSTOM("사용자 지정 게임");

    private final String description;

    public boolean isMatch() {
        return this == MATCH;
    }

    public boolean isPractice() {
        return this == PRACTICE;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }
}
