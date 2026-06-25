package com.sang.leagueofstar.auth.handler;

import com.sang.leagueofstar.auth.repository.OAuthLoginCodeStore;
import com.sang.leagueofstar.auth.security.dto.PrincipalDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String CODE_PARAMETER_NAME = "code";
    private static final long OAUTH_LOGIN_CODE_TTL_SECONDS = 180L;

    private final String successRedirectUrl;
    private final OAuthLoginCodeStore oauthLoginCodeStore;

    public OAuth2AuthenticationSuccessHandler(
            @Value("${oauth2.success-redirect-url}") String successRedirectUrl,
            OAuthLoginCodeStore oauthLoginCodeStore
    ) {
        this.successRedirectUrl = successRedirectUrl;
        this.oauthLoginCodeStore = oauthLoginCodeStore;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        PrincipalDetails principalDetails = (PrincipalDetails) authentication.getPrincipal();
        String code = generateCode();
        oauthLoginCodeStore.save(code, principalDetails.getUserId(), OAUTH_LOGIN_CODE_TTL_SECONDS);

        String targetUrl = determineTargetUrl(code);
        
        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String determineTargetUrl(String code) {
        // 프론트엔드 리다이렉트 경로
        return UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam(CODE_PARAMETER_NAME, code)
                .build().toUriString();
    }

    private String generateCode() {
        return UUID.randomUUID().toString();
    }
}
