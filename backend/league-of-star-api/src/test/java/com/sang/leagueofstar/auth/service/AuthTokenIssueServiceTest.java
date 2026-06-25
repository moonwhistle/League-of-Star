package com.sang.leagueofstar.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sang.leagueofstar.auth.domain.RefreshToken;
import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.leagueofstar.auth.infrastructure.token.RefreshTokenRepository;
import com.sang.leagueofstar.auth.service.dto.TokenDto;
import com.sang.leagueofstar.domain.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthTokenIssueServiceTest {

    @InjectMocks
    private AuthTokenIssueService authTokenIssueService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("사용자에게 access token과 refresh token을 발급하고 refresh token을 저장한다")
    void issueTokens() {
        // given
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();
        given(jwtTokenProvider.createAccessToken(user.getId(), user.getEmail())).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail())).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpirationMs()).willReturn(604_800_000L);

        // when
        TokenDto result = authTokenIssueService.issueTokens(user);

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(1)).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(refreshTokenCaptor.getValue().getToken()).isEqualTo("refresh-token");
        assertThat(refreshTokenCaptor.getValue().getTtl()).isEqualTo(604_800L);
    }
}
