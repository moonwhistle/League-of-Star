package com.sang.leagueofstar.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.common.response.ErrorResponse;
import com.sang.leagueofstar.global.exception.BaseErrorCode;
import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String jwt = resolveToken(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Authentication authentication = tokenProvider.getAuthentication(jwt);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Security Context에 '{}' 인증 정보를 저장했습니다", authentication.getName());
            }
            filterChain.doFilter(request, response);
        } catch (ApiException e) {
            log.warn("JWT 인증 실패: {}", e.getMessage());
            sendErrorResponse(response, e.getErrorCode());
        }
    }

    private void sendErrorResponse(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
