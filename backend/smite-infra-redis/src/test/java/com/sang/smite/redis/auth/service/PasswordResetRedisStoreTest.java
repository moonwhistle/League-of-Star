package com.sang.smite.redis.auth.service;

import com.sang.smite.redis.AbstractRedisRepositoryTest;
import com.sang.smite.redis.auth.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetRedisStoreTest extends AbstractRedisRepositoryTest {

    @Autowired
    private PasswordResetRedisStore passwordResetRedisStore;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    @DisplayName("비밀번호 재설정 토큰 저장 및 조회")
    void saveAndGetEmail() {
        // given
        String token = "test-token";
        String email = "test@example.com";
        long ttl = 10L;

        // when
        passwordResetRedisStore.save(token, email, ttl);
        Optional<String> foundEmail = passwordResetRedisStore.getEmailByToken(token);

        // then
        assertThat(foundEmail).isPresent();
        assertThat(foundEmail.get()).isEqualTo(email);
    }

    @Test
    @DisplayName("토큰 삭제 검증")
    void remove() {
        // given
        String token = "token-to-delete";
        String email = "delete@example.com";
        passwordResetRedisStore.save(token, email, 10L);

        // when
        passwordResetRedisStore.remove(token);
        Optional<String> foundEmail = passwordResetRedisStore.getEmailByToken(token);

        // then
        assertThat(foundEmail).isEmpty();
    }
}
