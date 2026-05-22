package com.sang.smite.game.smite.dto;

import com.sang.smite.game.result.dto.GameResultPayload;

import java.util.Optional;

public record GameSmiteHandleResponse(
        GameResultPayload gameResult,
        boolean broadcast
) {

    public static GameSmiteHandleResponse broadcast(GameResultPayload gameResult) {
        return new GameSmiteHandleResponse(gameResult, true);
    }

    public static GameSmiteHandleResponse currentSessionOnly(GameResultPayload gameResult) {
        return new GameSmiteHandleResponse(gameResult, false);
    }

    public Optional<GameResultPayload> gameResultOptional() {
        return Optional.ofNullable(gameResult);
    }
}
