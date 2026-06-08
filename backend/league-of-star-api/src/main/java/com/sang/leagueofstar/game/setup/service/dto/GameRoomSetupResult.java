package com.sang.leagueofstar.game.setup.service.dto;

import java.util.Objects;

/**
 * 게임 대기 화면 진입에 필요한 gameRoom 생성 결과입니다.
 */
public record GameRoomSetupResult(
        Long gameRoomId,
        String videoUrl,
        String webSocketUrl
) {

    public GameRoomSetupResult {
        Objects.requireNonNull(gameRoomId, "gameRoomId must not be null");
        Objects.requireNonNull(videoUrl, "videoUrl must not be null");
        Objects.requireNonNull(webSocketUrl, "webSocketUrl must not be null");
    }
}
