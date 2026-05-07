package com.sang.smite.auth.infrastructure.jwt;

import com.sang.smite.auth.security.dto.AuthenticatedUser;
import com.sang.smite.common.exception.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtTokenProvider의 기능을 검증하는 테스트.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        String secretKey = "c21pdGUta2V5LXNhbmctbGVhZ3VlLW9mLXNtaXRlLXNlY3JldC1rZXktZ2VuZXJhdGVkLWZvci1kZXZlbG9wbWVudA==";
        ReflectionTestUtils.setField(jwtTokenProvider, "salt", secretKey);
        long expiration = 3600000;
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpirationMs", expiration);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("유저 정보로 액세스 토큰을 생성할 수 있어야 한다")
    void createAccessToken_Success() {
        // given
        Long userId = 1L;
        String email = "test@test.com";

        // when
        String token = jwtTokenProvider.createAccessToken(userId, email);

        // then
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("생성된 토큰에서 Authentication 객체를 성공적으로 추출해야 한다")
    void getAuthentication_Success() {
        // given
        Long userId = 1L;
        String email = "test@test.com";
        String token = jwtTokenProvider.createAccessToken(userId, email);

        // when
        Authentication authentication = jwtTokenProvider.getAuthentication(token);

        // then
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.email()).isEqualTo(email);
        assertThat(authentication.getName()).isEqualTo(email); // getName()도 email을 반환해야 함
        assertThat(authentication.getAuthorities()).hasSize(1);
    }

    @Test
    @DisplayName("생성된 토큰에서 userId를 정확하게 추출해야 한다")
    void getUserId_Success() {
        // given
        Long userId = 12345L;
        String email = "verify@test.com";
        String token = jwtTokenProvider.createAccessToken(userId, email);

        // when
        Long extractedUserId = jwtTokenProvider.getUserId(token);

        // then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    @DisplayName("잘못된 토큰이나 만료된 토큰인 경우 ApiException이 발생해야 한다")
    void validateToken_Fail() {
        // given
        String invalidToken = "bearer.invalid.token";

        // when & then
        Assertions.assertThrows(ApiException.class, () -> {
            jwtTokenProvider.validateToken(invalidToken);
        });
    }
}
