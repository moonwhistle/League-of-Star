package com.sang.leagueofstar.game.result.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameResultReason {

    LIGHTNING_KILL("LIGHTNING_KILL"),
    NATURAL_DEATH_DRAW("NATURAL_DEATH_DRAW"),
    PRACTICE_LIGHTNING_KILL("PRACTICE_LIGHTNING_KILL"),
    PRACTICE_TIMEOUT("PRACTICE_TIMEOUT");

    private final String code;
}
