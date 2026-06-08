package com.sang.leagueofstar.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.sang.leagueofstar.redis.AbstractRedisTest;

@SpringBootTest
@ActiveProfiles("test")
class PasswordResetStoreTest extends AbstractRedisTest {

    @Autowired
    private PasswordResetStore passwordResetStore;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @AfterEach
    void tearDown() {
        connectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("비밀번호 재설정 토큰 저장 및 조회")
    void saveAndGetEmail() {
        // given
        String token = "test-token";
        String email = "test@example.com";
        long ttl = 10L;

        // when
        passwordResetStore.save(token, email, ttl);
        Optional<String> foundEmail = passwordResetStore.getEmailByToken(token);

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
        passwordResetStore.save(token, email, 10L);

        // when
        passwordResetStore.remove(token);
        Optional<String> foundEmail = passwordResetStore.getEmailByToken(token);

        // then
        assertThat(foundEmail).isEmpty();
    }
}
