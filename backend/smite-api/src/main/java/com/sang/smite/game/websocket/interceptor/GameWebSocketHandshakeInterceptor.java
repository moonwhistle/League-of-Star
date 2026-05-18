package com.sang.smite.game.websocket.interceptor;

import com.sang.smite.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.smite.auth.infrastructure.jwt.JwtTokenResolver;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.websocket.resolver.GameWebSocketPathResolver;
import com.sang.smite.game.websocket.session.GameWebSocketSessionAttribute;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GameWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenResolver jwtTokenResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final GameWebSocketPathResolver gameWebSocketPathResolver;
    private final GameRoomReadService gameRoomReadService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        try {
            String token = jwtTokenResolver.resolveWebSocketToken(request.getURI());
            jwtTokenProvider.validateToken(token);

            Long userId = jwtTokenProvider.getUserId(token);
            Long gameRoomId = gameWebSocketPathResolver.resolveGameRoomId(request.getURI());
            gameRoomReadService.validateReadyParticipant(gameRoomId, userId);

            attributes.put(GameWebSocketSessionAttribute.GAME_ROOM_ID, gameRoomId);
            attributes.put(GameWebSocketSessionAttribute.USER_ID, userId);

            return true;
        } catch (ApiException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } catch (IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        } catch (CoreException e) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
}
