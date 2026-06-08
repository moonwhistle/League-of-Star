package com.sang.leagueofstar.game.lightning.domain;

import com.sang.leagueofstar.game.lightning.common.constant.GameLightningConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameLightningFailureReason {

    INVALID_SMITE_STATE(GameLightningConstants.ERROR_INVALID_SMITE_STATE),
    NOT_GAME_PARTICIPANT(GameLightningConstants.ERROR_NOT_GAME_PARTICIPANT),
    INVALID_SMITE_PAYLOAD(GameLightningConstants.ERROR_INVALID_SMITE_PAYLOAD),
    SMITE_PROCESSING_FAILED(GameLightningConstants.ERROR_SMITE_PROCESSING_FAILED);

    private final String code;
}
