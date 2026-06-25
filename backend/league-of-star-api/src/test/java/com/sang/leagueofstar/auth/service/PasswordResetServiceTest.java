package com.sang.leagueofstar.auth.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.user.service.UserCommandService;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.sang.leagueofstar.auth.repository.PasswordResetStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_TOKEN = "test-uuid-token";
    private static final String NEW_RAW_PASSWORD = "newPassword123";
    private static final String ENCODED_PASSWORD = "encodedPassword123";
    private static final String FRONTEND_RESET_URL = "http://localhost:5173/password/reset";

    private PasswordResetService passwordResetService;

    @Mock
    private UserReadService userReadService;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private PasswordResetStore passwordResetStore;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userReadService,
                userCommandService,
                passwordResetStore,
                passwordEncoder,
                emailService,
                FRONTEND_RESET_URL
        );
    }

    @Test
    @DisplayName("requestReset - 성공: 유저가 존재하면 토큰을 생성하고 저장하며 이메일을 발송한다")
    void requestReset_Success() {
        // given
        given(userReadService.existsByEmail(TEST_EMAIL)).willReturn(true);

        // when
        String token = passwordResetService.requestReset(TEST_EMAIL);

        // then
        assertThat(token).isNotNull();
        verify(passwordResetStore, times(1)).save(anyString(), eq(TEST_EMAIL), anyLong());
        verify(emailService, times(1)).sendTextEmail(eq(TEST_EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("requestReset - 메일 링크는 프론트 reset URL과 token query로 생성한다")
    void requestReset_FrontendResetLink() {
        // given
        given(userReadService.existsByEmail(TEST_EMAIL)).willReturn(true);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        String token = passwordResetService.requestReset(TEST_EMAIL);

        // then
        verify(emailService).sendTextEmail(eq(TEST_EMAIL), anyString(), contentCaptor.capture());
        assertThat(contentCaptor.getValue())
                .contains(FRONTEND_RESET_URL + "?token=" + token)
                .doesNotContain("/api/v1/auth/password/reset-submit");
    }

    @Test
    @DisplayName("requestReset - 무시: 존재하지 않는 이메일이면 null을 반환하고 저장하지 않는다")
    void requestReset_NonExistentEmail() {
        // given
        given(userReadService.existsByEmail(TEST_EMAIL)).willReturn(false);

        // when
        String token = passwordResetService.requestReset(TEST_EMAIL);

        // then
        assertThat(token).isNull();
        verify(passwordResetStore, never()).save(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("requestReset - 메일 발송 실패는 요청 실패로 전파하지 않는다")
    void requestReset_EmailFailure_NoThrow() {
        // given
        given(userReadService.existsByEmail(TEST_EMAIL)).willReturn(true);
        willThrow(new RuntimeException("mail down"))
                .given(emailService)
                .sendTextEmail(eq(TEST_EMAIL), anyString(), anyString());

        // when & then
        assertThatCode(() -> passwordResetService.requestReset(TEST_EMAIL))
                .doesNotThrowAnyException();
        verify(passwordResetStore).save(anyString(), eq(TEST_EMAIL), anyLong());
    }

    @Test
    @DisplayName("resetPassword - 성공: 토큰이 유효하면 비밀번호를 암호화하여 업데이트한다")
    void resetPassword_Success() {
        // given
        given(passwordResetStore.getEmailByToken(TEST_TOKEN)).willReturn(Optional.of(TEST_EMAIL));
        given(passwordEncoder.encode(NEW_RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        // when
        passwordResetService.resetPassword(TEST_TOKEN, NEW_RAW_PASSWORD);

        // then
        verify(userCommandService).updatePasswordByEmail(TEST_EMAIL, ENCODED_PASSWORD);
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
        
        verify(userCommandService, never()).updatePasswordByEmail(anyString(), anyString());
    }
}
