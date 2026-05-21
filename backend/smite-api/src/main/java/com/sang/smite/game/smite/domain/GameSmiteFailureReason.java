package com.sang.smite.game.smite.domain;

import com.sang.smite.game.smite.common.constant.GameSmiteConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameSmiteFailureReason {

    INVALID_SMITE_STATE(GameSmiteConstants.ERROR_INVALID_SMITE_STATE),
    NOT_GAME_PARTICIPANT(GameSmiteConstants.ERROR_NOT_GAME_PARTICIPANT),
    SMITE_ALREADY_USED(GameSmiteConstants.ERROR_SMITE_ALREADY_USED),
    INVALID_SMITE_PAYLOAD(GameSmiteConstants.ERROR_INVALID_SMITE_PAYLOAD),
    SMITE_PROCESSING_FAILED(GameSmiteConstants.ERROR_SMITE_PROCESSING_FAILED);

    private final String code;
}
