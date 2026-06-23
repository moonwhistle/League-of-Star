package com.sang.leagueofstar.customgame.config;

import com.sang.leagueofstar.customgame.websocket.handler.CustomRoomWebSocketHandler;
import com.sang.leagueofstar.customgame.websocket.interceptor.CustomRoomWebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class CustomRoomWebSocketConfig implements WebSocketConfigurer {

    private static final String CUSTOM_ROOM_WEBSOCKET_ENDPOINT = "/ws/custom-games/rooms/{roomId}";
    private static final String[] ALLOWED_ORIGIN_PATTERNS = {"*"};

    private final CustomRoomWebSocketHandler customRoomWebSocketHandler;
    private final CustomRoomWebSocketHandshakeInterceptor customRoomWebSocketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(customRoomWebSocketHandler, CUSTOM_ROOM_WEBSOCKET_ENDPOINT)
                .addInterceptors(customRoomWebSocketHandshakeInterceptor)
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    }
}
