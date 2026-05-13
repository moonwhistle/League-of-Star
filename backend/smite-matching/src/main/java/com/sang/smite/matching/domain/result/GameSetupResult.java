package com.sang.smite.matching.domain.result;

import java.util.Objects;

/**
 * 매칭 성공 이벤트에 포함할 게임 대기 진입 정보입니다.
 */
public record GameSetupResult(
        Long gameRoomId,
        String videoUrl,
        String webSocketUrl
) {

    public GameSetupResult {
        Objects.requireNonNull(gameRoomId, "gameRoomId must not be null");
        Objects.requireNonNull(videoUrl, "videoUrl must not be null");
        Objects.requireNonNull(webSocketUrl, "webSocketUrl must not be null");
    }
}
