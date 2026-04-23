package com.sang.smite.auth.handler;

import com.sang.smite.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.smite.auth.security.dto.PrincipalDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String TOKEN_PARAMETER_NAME = "accessToken";

    private final String successRedirectUrl;
    private final JwtTokenProvider tokenProvider;

    public OAuth2AuthenticationSuccessHandler(
            @Value("${oauth2.success-redirect-url}") String successRedirectUrl,
            JwtTokenProvider tokenProvider
    ) {
        this.successRedirectUrl = successRedirectUrl;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        PrincipalDetails principalDetails = (PrincipalDetails) authentication.getPrincipal();
        
        String accessToken = tokenProvider.createAccessToken(
                principalDetails.getUserId(),
                principalDetails.getUsername()
        );

        String targetUrl = determineTargetUrl(accessToken);
        
        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String determineTargetUrl(String token) {
        // 프론트엔드 리다이렉트 경로
        return UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam(TOKEN_PARAMETER_NAME, token)
                .build().toUriString();
    }
}
