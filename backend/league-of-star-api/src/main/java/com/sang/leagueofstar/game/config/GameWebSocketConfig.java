package com.sang.leagueofstar.game.config;

import com.sang.leagueofstar.game.websocket.handler.GameWaitingWebSocketHandler;
import com.sang.leagueofstar.game.websocket.interceptor.GameWebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class GameWebSocketConfig implements WebSocketConfigurer {

    private static final String GAME_WEBSOCKET_ENDPOINT = "/ws/game/{gameRoomId}";
    private static final String[] ALLOWED_ORIGIN_PATTERNS = {"*"};

    private final GameWaitingWebSocketHandler gameWaitingWebSocketHandler;
    private final GameWebSocketHandshakeInterceptor gameWebSocketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWaitingWebSocketHandler, GAME_WEBSOCKET_ENDPOINT)
                .addInterceptors(gameWebSocketHandshakeInterceptor)
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    }
}
