package com.sang.leagueofstar.auth.service;

import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.leagueofstar.auth.domain.RefreshToken;
import com.sang.leagueofstar.auth.service.dto.LoginDto;
import com.sang.leagueofstar.auth.service.dto.TokenDto;
import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserCommandService;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.auth.infrastructure.token.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

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

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthTokenIssueService authTokenIssueService;

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
                .hasMessage(ApiErrorCode.AUTH_DUPLICATE_NICKNAME.customCode() + ": "
                        + ApiErrorCode.AUTH_DUPLICATE_NICKNAME.message());

        verify(userCommandService, never()).signup(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("로그인 - 성공")
    void login_Success() {
        // given
        String email = "test@example.com";
        String password = "password123";
        User user = User.builder().id(1L).email(email).password("encoded").build();

        given(userReadService.findByEmail(email)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(password, user.getPassword())).willReturn(true);
        given(authTokenIssueService.issueTokens(user)).willReturn(new TokenDto("access-token", "refresh-token"));

        // when
        LoginDto result = authService.login(email, password);

        // then
        assertThat(result.tokens().accessToken()).isEqualTo("access-token");
        assertThat(result.tokens().refreshToken()).isEqualTo("refresh-token");
        assertThat(result.user().getEmail()).isEqualTo(email);
        verify(authTokenIssueService, times(1)).issueTokens(user);
    }

    @Test
    @DisplayName("로그인 - 비밀번호 불일치 시 실패")
    void login_WrongPassword() {
        // given
        String email = "test@example.com";
        User user = User.builder().email(email).password("encoded").build();
        given(userReadService.findByEmail(email)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(email, "wrong-password"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiErrorCode.AUTH_LOGIN_FAILED.customCode());
    }

    @Test
    @DisplayName("토큰 재발급 - 성공")
    void refresh_Success() {
        // given
        String oldRefreshToken = "old-rt";
        User user = User.builder().id(1L).email("test@example.com").build();
        Authentication authentication = mock(Authentication.class);

        given(jwtTokenProvider.validateToken(oldRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByToken(oldRefreshToken)).willReturn(Optional.of(mock(RefreshToken.class)));
        given(jwtTokenProvider.getAuthentication(oldRefreshToken)).willReturn(authentication);
        given(authentication.getName()).willReturn(user.getEmail());
        given(userReadService.findByEmail(user.getEmail())).willReturn(Optional.of(user));
        
        given(authTokenIssueService.issueTokens(user)).willReturn(new TokenDto("new-at", "new-rt"));

        // when
        TokenDto result = authService.refresh(oldRefreshToken);

        // then
        assertThat(result.accessToken()).isEqualTo("new-at");
        assertThat(result.refreshToken()).isEqualTo("new-rt");
        verify(authTokenIssueService, times(1)).issueTokens(user);
    }

    @Test
    @DisplayName("로그아웃 - 성공")
    void logout_Success() {
        // given
        String refreshToken = "rt-to-delete";
        RefreshToken savedToken = mock(RefreshToken.class);
        given(refreshTokenRepository.findByToken(refreshToken)).willReturn(Optional.of(savedToken));

        // when
        authService.logout(refreshToken);

        // then
        verify(refreshTokenRepository, times(1)).delete(savedToken);
    }
}
