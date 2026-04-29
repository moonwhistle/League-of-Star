package com.sang.smite.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sang.smite.auth.domain.RefreshToken;
import com.sang.smite.auth.infrastructure.token.RefreshTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.sang.smite.redis.AbstractRedisTest;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenRepositoryTest extends AbstractRedisTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @AfterEach
    void tearDown() {
        connectionFactory.getConnection().serverCommands().flushAll();
    }

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
