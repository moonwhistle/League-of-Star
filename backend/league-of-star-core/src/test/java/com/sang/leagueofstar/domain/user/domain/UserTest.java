package com.sang.leagueofstar.domain.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("User 빌더를 통해 객체를 생성할 수 있다")
    void createUser() {
        // given
        String email = "test@example.com";
        String nickname = "테스터";
        String password = "encodedPassword";

        // when
        User user = User.builder()
                .email(email)
                .nickname(nickname)
                .password(password)
                .build();

        // then
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertThat(user.getPassword()).isEqualTo(password);
    }
}
