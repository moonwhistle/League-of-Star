package com.sang.leagueofstar.auth.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sang.leagueofstar.auth.repository.OAuthLoginCodeStore;
import com.sang.leagueofstar.auth.security.dto.PrincipalDetails;
import com.sang.leagueofstar.domain.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    private static final String SUCCESS_REDIRECT_URL = "http://localhost:5173/oauth2/redirect";

    @Mock
    private OAuthLoginCodeStore oauthLoginCodeStore;

    @Test
    @DisplayName("OAuth 성공 시 token을 URL에 싣지 않고 one-time code만 redirect한다")
    void redirectWithCodeOnly() throws Exception {
        // given
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                SUCCESS_REDIRECT_URL,
                oauthLoginCodeStore
        );
        User user = User.builder()
                .id(1L)
                .email("oauth@example.com")
                .build();
        PrincipalDetails principalDetails = new PrincipalDetails(user);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(principalDetails, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).startsWith(SUCCESS_REDIRECT_URL);
        assertThat(redirectUrl).contains("code=");
        assertThat(redirectUrl).doesNotContain("accessToken");
        assertThat(redirectUrl).doesNotContain("refreshToken");

        String code = UriComponentsBuilder.fromUriString(redirectUrl)
                .build()
                .getQueryParams()
                .getFirst("code");
        assertThat(code).isNotBlank();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(oauthLoginCodeStore, times(1)).save(codeCaptor.capture(), eq(user.getId()), eq(180L));
        assertThat(codeCaptor.getValue()).isEqualTo(code);
    }
}
