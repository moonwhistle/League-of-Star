package com.sang.leagueofstar.customgame.config;

import com.sang.leagueofstar.customgame.websocket.handler.CustomRoomWebSocketHandler;
import com.sang.leagueofstar.customgame.websocket.interceptor.CustomRoomWebSocketHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CustomRoomWebSocketConfig implements WebSocketConfigurer {

    private static final String CUSTOM_ROOM_WEBSOCKET_ENDPOINT = "/ws/custom-games/rooms/{roomId}";

    private final CustomRoomWebSocketHandler customRoomWebSocketHandler;
    private final CustomRoomWebSocketHandshakeInterceptor customRoomWebSocketHandshakeInterceptor;
    private final String[] allowedOriginPatterns;

    public CustomRoomWebSocketConfig(
            CustomRoomWebSocketHandler customRoomWebSocketHandler,
            CustomRoomWebSocketHandshakeInterceptor customRoomWebSocketHandshakeInterceptor,
            @Value("${app.websocket.allowed-origin-patterns}") String[] allowedOriginPatterns
    ) {
        this.customRoomWebSocketHandler = customRoomWebSocketHandler;
        this.customRoomWebSocketHandshakeInterceptor = customRoomWebSocketHandshakeInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(customRoomWebSocketHandler, CUSTOM_ROOM_WEBSOCKET_ENDPOINT)
                .addInterceptors(customRoomWebSocketHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
