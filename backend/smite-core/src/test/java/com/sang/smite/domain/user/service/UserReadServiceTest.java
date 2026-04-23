package com.sang.smite.domain.user.service;

import com.sang.smite.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserReadServiceTest {

    @InjectMocks
    private UserReadService userReadService;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("existsByEmail - 이메일 존재 여부를 반환한다")
    void existsByEmail_ReturnStatus() {
        // given
        String email = "test@example.com";
        given(userRepository.existsByEmail(email)).willReturn(true);

        // when
        boolean result = userReadService.existsByEmail(email);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsByNickname - 닉네임 존재 여부를 반환한다")
    void existsByNickname_ReturnStatus() {
        // given
        String nickname = "테스터";
        given(userRepository.existsByNickname(nickname)).willReturn(false);

        // when
        boolean result = userReadService.existsByNickname(nickname);

        // then
        assertThat(result).isFalse();
    }
}
