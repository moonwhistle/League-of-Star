package com.sang.smite.game.smite.dto;

import java.util.Optional;

public record GameSmiteHandleResponse(
        SmiteResultPayload smiteResult,
        GameResultPayload gameResult
) {

    public static GameSmiteHandleResponse smiteOnly(SmiteResultPayload smiteResult) {
        return new GameSmiteHandleResponse(smiteResult, null);
    }

    public static GameSmiteHandleResponse withGameResult(SmiteResultPayload smiteResult,
                                                         GameResultPayload gameResult) {
        return new GameSmiteHandleResponse(smiteResult, gameResult);
    }

    public static GameSmiteHandleResponse gameResultOnly(GameResultPayload gameResult) {
        return new GameSmiteHandleResponse(null, gameResult);
    }

    public Optional<SmiteResultPayload> smiteResultOptional() {
        return Optional.ofNullable(smiteResult);
    }

    public Optional<GameResultPayload> gameResultOptional() {
        return Optional.ofNullable(gameResult);
    }
}
