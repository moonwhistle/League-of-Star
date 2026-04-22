package com.sang.smite.auth.service;

import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.service.UserCommandService;
import com.sang.smite.domain.user.service.UserReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserReadService userReadService;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입 - 성공")
    void signUp_Success() {
        // given
        String email = "test@example.com";
        String password = "password123";
        String nickname = "테스터";
        String encodedPassword = "encodedPassword123";

        given(userReadService.existsByEmail(email)).willReturn(false);
        given(userReadService.existsByNickname(nickname)).willReturn(false);
        given(passwordEncoder.encode(password)).willReturn(encodedPassword);
        
        User user = User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .build();
        given(userCommandService.signup(email, encodedPassword, nickname)).willReturn(user);

        // when
        User result = authService.signUp(email, password, nickname);

        // then
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getNickname()).isEqualTo(nickname);
        verify(userCommandService, times(1)).signup(email, encodedPassword, nickname);
    }

    @Test
    @DisplayName("회원가입 - 이메일 중복 시 실패")
    void signUp_DuplicateEmail() {
        // given
        String email = "duplicate@example.com";
        given(userReadService.existsByEmail(email)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(email, "password", "nickname"))
                .isInstanceOf(ApiException.class)
                .hasMessage(ApiErrorCode.AUTH_DUPLICATE_EMAIL.customCode() + ": " + ApiErrorCode.AUTH_DUPLICATE_EMAIL.message());

        verify(userCommandService, never()).signup(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("회원가입 - 닉네임 중복 시 실패")
    void signUp_DuplicateNickname() {
        // given
        String email = "test@example.com";
        String nickname = "duplicateNick";
        given(userReadService.existsByEmail(email)).willReturn(false);
        given(userReadService.existsByNickname(nickname)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(email, "password", nickname))
                .isInstanceOf(ApiException.class)
                .hasMessage(ApiErrorCode.AUTH_DUPLICATE_NICKNAME.customCode() + ": " + ApiErrorCode.AUTH_DUPLICATE_NICKNAME.message());

        verify(userCommandService, never()).signup(anyString(), anyString(), anyString());
    }
}
