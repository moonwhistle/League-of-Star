package com.sang.leagueofstar.game.lightning.dto;

import com.sang.leagueofstar.game.result.dto.GameResultPayload;

import java.util.Optional;

public record GameLightningHandleResponse(
        GameLightningAppliedPayload lightningApplied,
        GameResultPayload gameResult,
        boolean gameResultBroadcast
) {

    public static GameLightningHandleResponse applied(GameLightningAppliedPayload lightningApplied) {
        return new GameLightningHandleResponse(lightningApplied, null, false);
    }

    public static GameLightningHandleResponse appliedAndBroadcastResult(GameLightningAppliedPayload lightningApplied,
                                                                        GameResultPayload gameResult) {
        return new GameLightningHandleResponse(lightningApplied, gameResult, true);
    }

    public static GameLightningHandleResponse broadcast(GameResultPayload gameResult) {
        return new GameLightningHandleResponse(null, gameResult, true);
    }

    public static GameLightningHandleResponse currentSessionOnly(GameResultPayload gameResult) {
        return new GameLightningHandleResponse(null, gameResult, false);
    }

    public Optional<GameLightningAppliedPayload> lightningAppliedOptional() {
        return Optional.ofNullable(lightningApplied);
    }

    public Optional<GameResultPayload> gameResultOptional() {
        return Optional.ofNullable(gameResult);
    }
}
