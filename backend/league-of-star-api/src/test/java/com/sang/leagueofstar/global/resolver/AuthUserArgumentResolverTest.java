package com.sang.leagueofstar.global.resolver;

import com.sang.leagueofstar.auth.security.dto.AuthenticatedUser;
import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserArgumentResolverTest {

    private final AuthUserArgumentResolver resolver = new AuthUserArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("principal이 AuthenticatedUser면 userId를 추출한다.")
    void resolveAuthenticatedUserPrincipal() throws Exception {
        // given
        AuthenticatedUser principal = new AuthenticatedUser(
                123L,
                "test@smite.com",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities())
        );

        MethodParameter parameter = param("requiredUser");

        // when
        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        // then
        assertThat(resolved).isEqualTo(123L);
    }

    @Test
    @DisplayName("principal이 Long이면 그대로 반환한다.")
    void resolveLongPrincipal() throws Exception {
        // given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(999L, "token")
        );

        MethodParameter parameter = param("requiredUser");

        // when
        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        // then
        assertThat(resolved).isEqualTo(999L);
    }

    @Test
    @DisplayName("인증이 없고 required=true면 AUTH_UNAUTHORIZED 예외를 던진다.")
    void requiredWhenAuthenticationMissing() throws Exception {
        MethodParameter parameter = param("requiredUser");

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ApiErrorCode.AUTH_UNAUTHORIZED);
    }

    @Test
    @DisplayName("인증이 없고 required=false면 null을 반환한다.")
    void optionalWhenAuthenticationMissing() throws Exception {
        MethodParameter parameter = param("optionalUser");

        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("anonymousUser는 인증 실패로 처리한다.")
    void anonymousUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
        );

        MethodParameter required = param("requiredUser");
        MethodParameter optional = param("optionalUser");

        assertThatThrownBy(() -> resolver.resolveArgument(required, null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ApiErrorCode.AUTH_UNAUTHORIZED);

        Object resolved = resolver.resolveArgument(optional, null, null, null);
        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("@AuthUser Long 파라미터만 지원한다.")
    void supportsParameter() throws Exception {
        assertThat(resolver.supportsParameter(param("requiredUser"))).isTrue();
        assertThat(resolver.supportsParameter(param("stringUser"))).isFalse();
        assertThat(resolver.supportsParameter(param("plainLong"))).isFalse();
    }

    private MethodParameter param(String methodName) throws Exception {
        Method method;
        switch (methodName) {
            case "requiredUser" -> method = DummyController.class.getDeclaredMethod("requiredUser", Long.class);
            case "optionalUser" -> method = DummyController.class.getDeclaredMethod("optionalUser", Long.class);
            case "stringUser" -> method = DummyController.class.getDeclaredMethod("stringUser", String.class);
            case "plainLong" -> method = DummyController.class.getDeclaredMethod("plainLong", Long.class);
            default -> throw new IllegalArgumentException("Unknown method: " + methodName);
        }
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static class DummyController {
        void requiredUser(@AuthUser Long userId) {
        }

        void optionalUser(@AuthUser(required = false) Long userId) {
        }

        void stringUser(@AuthUser String userId) {
        }

        void plainLong(Long userId) {
        }
    }
}
