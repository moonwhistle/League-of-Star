package com.sang.leagueofstar.game.lightning.dto;

import com.sang.leagueofstar.game.result.dto.GameResultPayload;

import java.util.Optional;

public record GameLightningHandleResponse(
        GameResultPayload gameResult,
        boolean broadcast
) {

    public static GameLightningHandleResponse broadcast(GameResultPayload gameResult) {
        return new GameLightningHandleResponse(gameResult, true);
    }

    public static GameLightningHandleResponse currentSessionOnly(GameResultPayload gameResult) {
        return new GameLightningHandleResponse(gameResult, false);
    }

    public Optional<GameResultPayload> gameResultOptional() {
        return Optional.ofNullable(gameResult);
    }
}
