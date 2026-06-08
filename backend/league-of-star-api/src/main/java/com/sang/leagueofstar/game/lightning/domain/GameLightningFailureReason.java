package com.sang.leagueofstar.game.lightning.domain;

import com.sang.leagueofstar.game.lightning.common.constant.GameLightningConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameLightningFailureReason {

    INVALID_LIGHTNING_STATE(GameLightningConstants.ERROR_INVALID_LIGHTNING_STATE),
    NOT_GAME_PARTICIPANT(GameLightningConstants.ERROR_NOT_GAME_PARTICIPANT),
    INVALID_LIGHTNING_PAYLOAD(GameLightningConstants.ERROR_INVALID_LIGHTNING_PAYLOAD),
    LIGHTNING_PROCESSING_FAILED(GameLightningConstants.ERROR_LIGHTNING_PROCESSING_FAILED);

    private final String code;
}
