package com.sang.smite.auth.infrastructure.jwt;

import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenResolverTest {

    private static final String TOKEN = "access-token";

    private final JwtTokenResolver jwtTokenResolver = new JwtTokenResolver();

    @Test
    @DisplayName("resolveWebSocketToken - query parameter에서 token을 추출한다")
    void resolveWebSocketToken_Success() {
        // when
        String result = jwtTokenResolver.resolveWebSocketToken(
                URI.create("http://localhost/ws/game/100?token=" + TOKEN)
        );

        // then
        assertThat(result).isEqualTo(TOKEN);
    }

    @Test
    @DisplayName("resolveWebSocketToken - token이 없으면 인증 예외를 던진다")
    void resolveWebSocketToken_MissingToken_ThrowException() {
        assertThatThrownBy(() -> jwtTokenResolver.resolveWebSocketToken(
                URI.create("http://localhost/ws/game/100")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_UNAUTHORIZED));
    }

    @Test
    @DisplayName("resolveWebSocketToken - token이 blank이면 인증 예외를 던진다")
    void resolveWebSocketToken_BlankToken_ThrowException() {
        assertThatThrownBy(() -> jwtTokenResolver.resolveWebSocketToken(
                URI.create("http://localhost/ws/game/100?token=%20")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_UNAUTHORIZED));
    }
}
