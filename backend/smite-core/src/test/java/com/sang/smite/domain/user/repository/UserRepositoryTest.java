package com.sang.smite.domain.user.repository;

import com.sang.smite.domain.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("이메일로 사용자가 존재하는지 확인한다")
    void existsByEmail() {
        // given
        String email = "test@example.com";
        User user = User.builder()
                .email(email)
                .nickname("테스터")
                .build();
        userRepository.save(user);

        // when
        boolean exists = userRepository.existsByEmail(email);
        boolean notExists = userRepository.existsByEmail("other@example.com");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("닉네임으로 사용자가 존재하는지 확인한다")
    void existsByNickname() {
        // given
        String nickname = "테스터";
        User user = User.builder()
                .email("test@example.com")
                .nickname(nickname)
                .build();
        userRepository.save(user);

        // when
        boolean exists = userRepository.existsByNickname(nickname);
        boolean notExists = userRepository.existsByNickname("다른닉네임");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
}
