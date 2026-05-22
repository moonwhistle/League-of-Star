package com.sang.smite.game.smite.domain;

import com.sang.smite.game.smite.common.constant.GameSmiteConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameSmiteResultReason {

    SMITE_KILL(GameSmiteConstants.RESULT_REASON_SMITE_KILL),
    BOTH_SMITES_USED_DRAW(GameSmiteConstants.RESULT_REASON_BOTH_SMITES_USED_DRAW);

    private final String code;
}
