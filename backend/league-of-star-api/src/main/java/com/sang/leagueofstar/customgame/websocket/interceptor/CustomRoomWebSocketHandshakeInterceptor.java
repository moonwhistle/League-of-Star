package com.sang.leagueofstar.customgame.websocket.interceptor;

import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenResolver;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.customgame.websocket.resolver.CustomRoomWebSocketPathResolver;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionAttribute;
import com.sang.leagueofstar.domain.customgame.service.CustomGameRoomReadService;
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
public class CustomRoomWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenResolver jwtTokenResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomRoomWebSocketPathResolver customRoomWebSocketPathResolver;
    private final CustomGameRoomReadService customGameRoomReadService;

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
            Long customRoomId = customRoomWebSocketPathResolver.resolveRoomId(request.getURI());
            customGameRoomReadService.validateWaitingParticipant(customRoomId, userId);

            attributes.put(CustomRoomWebSocketSessionAttribute.CUSTOM_ROOM_ID, customRoomId);
            attributes.put(CustomRoomWebSocketSessionAttribute.USER_ID, userId);

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
