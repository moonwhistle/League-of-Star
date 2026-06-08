package com.sang.leagueofstar.game.result.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameResultReason {

    LIGHTNING_KILL("LIGHTNING_KILL"),
    BOTH_LIGHTNINGS_USED_DRAW("BOTH_LIGHTNINGS_USED_DRAW"),
    NATURAL_DEATH_DRAW("NATURAL_DEATH_DRAW");

    private final String code;
}
