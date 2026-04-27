package com.sang.smite.auth.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import com.sang.smite.domain.user.service.PasswordResetStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_TOKEN = "test-uuid-token";
    private static final String NEW_RAW_PASSWORD = "newPassword123";
    private static final String ENCODED_PASSWORD = "encodedPassword123";

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetStore passwordResetStore;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("requestReset - 성공: 유저가 존재하면 토큰을 생성하고 저장한다")
    void requestReset_Success() {
        // given
        given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(true);

        // when
        String token = passwordResetService.requestReset(TEST_EMAIL);

        // then
        assertThat(token).isNotNull();
        verify(passwordResetStore, times(1)).save(anyString(), eq(TEST_EMAIL), anyLong());
    }

    @Test
    @DisplayName("requestReset - 무시: 존재하지 않는 이메일이면 null을 반환하고 저장하지 않는다")
    void requestReset_NonExistentEmail() {
        // given
        given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(false);

        // when
        String token = passwordResetService.requestReset(TEST_EMAIL);

        // then
        assertThat(token).isNull();
        verify(passwordResetStore, never()).save(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("resetPassword - 성공: 토큰이 유효하면 비밀번호를 암호화하여 업데이트한다")
    void resetPassword_Success() {
        // given
        User user = User.builder().email(TEST_EMAIL).build();
        given(passwordResetStore.getEmailByToken(TEST_TOKEN)).willReturn(Optional.of(TEST_EMAIL));
        given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.encode(NEW_RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        // when
        passwordResetService.resetPassword(TEST_TOKEN, NEW_RAW_PASSWORD);

        // then
        assertThat(user.getPassword()).isEqualTo(ENCODED_PASSWORD);
        verify(passwordResetStore, times(1)).remove(TEST_TOKEN);
    }

    @Test
    @DisplayName("resetPassword - 실패: 유효하지 않은 토큰이면 예외가 발생한다")
    void resetPassword_InvalidToken() {
        // given
        given(passwordResetStore.getEmailByToken(TEST_TOKEN)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> passwordResetService.resetPassword(TEST_TOKEN, NEW_RAW_PASSWORD))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", CoreErrorCode.INVALID_RESET_TOKEN);
        
        verify(userRepository, never()).findByEmail(anyString());
    }
}
