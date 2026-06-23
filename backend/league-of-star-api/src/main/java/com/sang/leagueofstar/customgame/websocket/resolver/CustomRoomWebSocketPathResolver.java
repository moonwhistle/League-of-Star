package com.sang.leagueofstar.customgame.websocket.resolver;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
public class CustomRoomWebSocketPathResolver {

    private static final String PATH_SEPARATOR = "/";

    public Long resolveRoomId(URI uri) {
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("WebSocket customRoomId path is required.");
        }

        int lastSeparatorIndex = path.lastIndexOf(PATH_SEPARATOR);
        if (lastSeparatorIndex < 0 || lastSeparatorIndex == path.length() - PATH_SEPARATOR.length()) {
            throw new IllegalArgumentException("WebSocket customRoomId path is invalid.");
        }

        long roomId = Long.parseLong(path.substring(lastSeparatorIndex + PATH_SEPARATOR.length()));
        if (roomId <= 0) {
            throw new IllegalArgumentException("WebSocket customRoomId must be positive.");
        }
        return roomId;
    }
}
