package com.sang.smite.domain.user.service;

import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @InjectMocks
    private UserCommandService userCommandService;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("signup - 성공적으로 유저를 저장한다")
    void signup_Success() {
        // given
        String email = "test@example.com";
        String password = "encodedPassword";
        String nickname = "테스터";
        
        User user = User.builder()
                .id(1L)
                .email(email)
                .password(password)
                .nickname(nickname)
                .build();
        
        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        User result = userCommandService.signup(email, password, nickname);

        // then
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getNickname()).isEqualTo(nickname);
        verify(userRepository, times(1)).save(any(User.class));
    }
}
