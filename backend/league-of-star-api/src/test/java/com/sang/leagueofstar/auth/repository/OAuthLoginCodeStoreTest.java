package com.sang.leagueofstar.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sang.leagueofstar.redis.AbstractRedisTest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OAuthLoginCodeStoreTest extends AbstractRedisTest {

    @Autowired
    private OAuthLoginCodeStore oauthLoginCodeStore;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @AfterEach
    void tearDown() {
        connectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("OAuth 로그인 code 저장 및 조회")
    void saveAndGetUserId() {
        // given
        String code = "oauth-code";
        Long userId = 1L;

        // when
        oauthLoginCodeStore.save(code, userId, 180L);
        Optional<Long> foundUserId = oauthLoginCodeStore.getUserIdByCode(code);

        // then
        assertThat(foundUserId).isPresent();
        assertThat(foundUserId.get()).isEqualTo(userId);
    }

    @Test
    @DisplayName("OAuth 로그인 code 삭제 검증")
    void remove() {
        // given
        String code = "oauth-code-to-delete";
        oauthLoginCodeStore.save(code, 2L, 180L);

        // when
        oauthLoginCodeStore.remove(code);
        Optional<Long> foundUserId = oauthLoginCodeStore.getUserIdByCode(code);

        // then
        assertThat(foundUserId).isEmpty();
    }
}
