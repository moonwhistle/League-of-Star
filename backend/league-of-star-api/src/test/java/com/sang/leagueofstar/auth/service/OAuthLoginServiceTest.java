package com.sang.leagueofstar.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sang.leagueofstar.auth.repository.OAuthLoginCodeStore;
import com.sang.leagueofstar.auth.service.dto.LoginDto;
import com.sang.leagueofstar.auth.service.dto.TokenDto;
import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

    @InjectMocks
    private OAuthLoginService oauthLoginService;

    @Mock
    private OAuthLoginCodeStore oauthLoginCodeStore;

    @Mock
    private UserReadService userReadService;

    @Mock
    private AuthTokenIssueService authTokenIssueService;

    @Test
    @DisplayName("유효한 OAuth code를 기존 로그인 응답과 같은 token pair로 교환한다")
    void exchangeCode() {
        // given
        String code = "oauth-code";
        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .email("oauth@example.com")
                .nickname("StarUser")
                .build();
        given(oauthLoginCodeStore.consumeUserIdByCode(code)).willReturn(Optional.of(userId));
        given(userReadService.findById(userId)).willReturn(user);
        given(authTokenIssueService.issueTokens(user)).willReturn(new TokenDto("access-token", "refresh-token"));

        // when
        LoginDto result = oauthLoginService.exchangeCode(code);

        // then
        assertThat(result.tokens().accessToken()).isEqualTo("access-token");
        assertThat(result.tokens().refreshToken()).isEqualTo("refresh-token");
        assertThat(result.user()).isEqualTo(user);
        verify(oauthLoginCodeStore, times(1)).consumeUserIdByCode(code);
        verify(oauthLoginCodeStore, never()).remove(code);
    }

    @Test
    @DisplayName("유효하지 않은 OAuth code는 token 발급 없이 실패한다")
    void exchangeCodeInvalidCode() {
        // given
        String code = "invalid-code";
        given(oauthLoginCodeStore.consumeUserIdByCode(code)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> oauthLoginService.exchangeCode(code))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiErrorCode.AUTH_INVALID_OAUTH_CODE.customCode());
        verify(userReadService, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(authTokenIssueService, never()).issueTokens(org.mockito.ArgumentMatchers.any(User.class));
        verify(oauthLoginCodeStore, never()).remove(code);
    }
}
