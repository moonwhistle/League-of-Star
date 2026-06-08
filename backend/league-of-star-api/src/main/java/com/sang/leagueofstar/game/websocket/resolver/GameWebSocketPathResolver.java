package com.sang.leagueofstar.game.websocket.resolver;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
public class GameWebSocketPathResolver {

    private static final String PATH_SEPARATOR = "/";

    public Long resolveGameRoomId(URI uri) {
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("WebSocket gameRoomId path is required.");
        }

        int lastSeparatorIndex = path.lastIndexOf(PATH_SEPARATOR);
        if (lastSeparatorIndex < 0 || lastSeparatorIndex == path.length() - PATH_SEPARATOR.length()) {
            throw new IllegalArgumentException("WebSocket gameRoomId path is invalid.");
        }

        long gameRoomId = Long.parseLong(path.substring(lastSeparatorIndex + PATH_SEPARATOR.length()));
        if (gameRoomId <= 0) {
            throw new IllegalArgumentException("WebSocket gameRoomId must be positive.");
        }
        return gameRoomId;
    }
}
