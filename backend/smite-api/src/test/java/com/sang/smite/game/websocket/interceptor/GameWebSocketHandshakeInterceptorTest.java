package com.sang.smite.game.websocket.interceptor;

import com.sang.smite.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.smite.auth.infrastructure.jwt.JwtTokenResolver;
import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.game.websocket.resolver.GameWebSocketPathResolver;
import com.sang.smite.game.websocket.session.GameWebSocketSessionAttribute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameWebSocketHandshakeInterceptorTest {

    private static final String TOKEN = "access-token";
    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;

    @InjectMocks
    private GameWebSocketHandshakeInterceptor interceptor;

    @Mock
    private JwtTokenResolver jwtTokenResolver;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private GameWebSocketPathResolver gameWebSocketPathResolver;

    @Mock
    private GameRoomReadService gameRoomReadService;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler webSocketHandler;

    @Test
    @DisplayName("beforeHandshake - JWT와 참가자 검증 성공 시 session attributes에 gameRoomId/userId를 저장한다")
    void beforeHandshake_Success() {
        // given
        URI uri = URI.create("http://localhost/ws/game/100?token=" + TOKEN);
        ServerHttpRequest request = request(uri);
        Map<String, Object> attributes = new HashMap<>();
        given(jwtTokenResolver.resolveWebSocketToken(uri)).willReturn(TOKEN);
        given(jwtTokenProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtTokenProvider.getUserId(TOKEN)).willReturn(USER_ID);
        given(gameWebSocketPathResolver.resolveGameRoomId(uri)).willReturn(GAME_ROOM_ID);

        // when
        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        // then
        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(GameWebSocketSessionAttribute.GAME_ROOM_ID, GAME_ROOM_ID)
                .containsEntry(GameWebSocketSessionAttribute.USER_ID, USER_ID);
        verify(gameRoomReadService).validateReadyParticipant(GAME_ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("beforeHandshake - token이 없으면 401로 handshake를 거부한다")
    void beforeHandshake_MissingToken_Reject() {
        // given
        URI uri = URI.create("http://localhost/ws/game/100");
        ServerHttpRequest request = request(uri);
        Map<String, Object> attributes = new HashMap<>();
        given(jwtTokenResolver.resolveWebSocketToken(uri))
                .willThrow(new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED));

        // when
        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        // then
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(jwtTokenProvider, never()).validateToken(TOKEN);
    }

    @Test
    @DisplayName("beforeHandshake - JWT가 유효하지 않으면 401로 handshake를 거부한다")
    void beforeHandshake_InvalidToken_Reject() {
        // given
        URI uri = URI.create("http://localhost/ws/game/100?token=" + TOKEN);
        ServerHttpRequest request = request(uri);
        Map<String, Object> attributes = new HashMap<>();
        given(jwtTokenResolver.resolveWebSocketToken(uri)).willReturn(TOKEN);
        given(jwtTokenProvider.validateToken(TOKEN))
                .willThrow(new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN));

        // when
        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        // then
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("beforeHandshake - gameRoomId path가 올바르지 않으면 400으로 handshake를 거부한다")
    void beforeHandshake_InvalidGameRoomIdPath_Reject() {
        // given
        URI uri = URI.create("http://localhost/ws/game/not-number?token=" + TOKEN);
        ServerHttpRequest request = request(uri);
        Map<String, Object> attributes = new HashMap<>();
        given(jwtTokenResolver.resolveWebSocketToken(uri)).willReturn(TOKEN);
        given(jwtTokenProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtTokenProvider.getUserId(TOKEN)).willReturn(USER_ID);
        given(gameWebSocketPathResolver.resolveGameRoomId(uri))
                .willThrow(new IllegalArgumentException("WebSocket gameRoomId path is invalid."));

        // when
        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        // then
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        verify(gameRoomReadService, never()).validateReadyParticipant(GAME_ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("beforeHandshake - gameRoom 참가자가 아니면 403으로 handshake를 거부한다")
    void beforeHandshake_NotParticipant_Reject() {
        // given
        URI uri = URI.create("http://localhost/ws/game/100?token=" + TOKEN);
        ServerHttpRequest request = request(uri);
        Map<String, Object> attributes = new HashMap<>();
        given(jwtTokenResolver.resolveWebSocketToken(uri)).willReturn(TOKEN);
        given(jwtTokenProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtTokenProvider.getUserId(TOKEN)).willReturn(USER_ID);
        given(gameWebSocketPathResolver.resolveGameRoomId(uri)).willReturn(GAME_ROOM_ID);
        willThrow(new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS))
                .given(gameRoomReadService)
                .validateReadyParticipant(GAME_ROOM_ID, USER_ID);

        // when
        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        // then
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("beforeHandshake - late handshake 시 gameRoom이 ABORTED라 READY 검증에 실패하면 거부한다")
    void beforeHandshake_AbortedGameRoom_Reject() {
        // given
        URI uri = URI.create("http://localhost/ws/game/100?token=" + TOKEN);
        ServerHttpRequest request = request(uri);
        Map<String, Object> attributes = new HashMap<>();
        given(jwtTokenResolver.resolveWebSocketToken(uri)).willReturn(TOKEN);
        given(jwtTokenProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtTokenProvider.getUserId(TOKEN)).willReturn(USER_ID);
        given(gameWebSocketPathResolver.resolveGameRoomId(uri)).willReturn(GAME_ROOM_ID);
        willThrow(new CoreException(CoreErrorCode.INVALID_GAME_STATE))
                .given(gameRoomReadService)
                .validateReadyParticipant(GAME_ROOM_ID, USER_ID);

        // when
        boolean result = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

        // then
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    private ServerHttpRequest request(URI uri) {
        ServerHttpRequest request = org.mockito.Mockito.mock(ServerHttpRequest.class);
        given(request.getURI()).willReturn(uri);
        return request;
    }
}
