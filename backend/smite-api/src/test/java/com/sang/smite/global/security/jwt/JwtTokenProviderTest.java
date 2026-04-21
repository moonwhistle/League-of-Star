package com.sang.smite.global.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secretKey = "c21pdGUta2V5LXNhbmctbGVhZ3VlLW9mLXNtaXRlLXNlY3JldC1rZXktZ2VuZXJhdGVkLWZvci1kZXZlbG9wbWVudA==";
    private final long expiration = 3600000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "salt", secretKey);
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
        assertThat(authentication.getName()).isEqualTo(email);
        assertThat(authentication.getAuthorities()).hasSize(1);
        assertThat(authentication.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
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
    @DisplayName("잘못된 토큰이나 만료된 토큰인 경우 검증에 실패해야 한다")
    void validateToken_Fail() {
        // given
        String invalidToken = "bearer.invalid.token";

        // when
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        // then
        assertThat(isValid).isFalse();
    }
}
