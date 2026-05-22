package com.sang.smite.game.result.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameResultReason {

    SMITE_KILL("SMITE_KILL"),
    BOTH_SMITES_USED_DRAW("BOTH_SMITES_USED_DRAW"),
    NATURAL_DEATH_DRAW("NATURAL_DEATH_DRAW");

    private final String code;
}
