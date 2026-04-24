package com.sang.smite.redis.auth.repository;

import com.sang.smite.redis.AbstractRedisRepositoryTest;
import com.sang.smite.redis.auth.domain.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRepositoryTest extends AbstractRedisRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("리프레시 토큰 저장 및 조회")
    void saveAndFind() {
        // given
        Long userId = 1L;
        String token = "test-refresh-token";
        RefreshToken refreshToken = RefreshToken.of(userId, token, 60L);

        // when
        refreshTokenRepository.save(refreshToken);
        Optional<RefreshToken> savedToken = refreshTokenRepository.findById(userId);

        // then
        assertThat(savedToken).isPresent();
        assertThat(savedToken.get().getToken()).isEqualTo(token);
    }

    @Test
    @DisplayName("토큰으로 리프레시 토큰 조회")
    void findByToken() {
        // given
        Long userId = 2L;
        String token = "token-to-find";
        refreshTokenRepository.save(RefreshToken.of(userId, token, 60L));

        // when
        Optional<RefreshToken> found = refreshTokenRepository.findByToken(token);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
    }
}
