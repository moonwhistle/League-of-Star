package com.sang.smite.auth.security.config;

import com.sang.smite.auth.service.CustomOAuth2UserService;
import com.sang.smite.common.path.SecurityPath;
import com.sang.smite.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.smite.auth.filter.JwtAuthenticationFilter;
import com.sang.smite.auth.handler.JwtAuthenticationExceptionHandler;
import com.sang.smite.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final JwtAuthenticationExceptionHandler authenticationExceptionHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless 기반 보안 설정
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 에러 핸들링
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationExceptionHandler)
            )

            // 인가 경로 설정
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(SecurityPath.AUTH_WHITELIST).permitAll()
                .anyRequest().authenticated()
            )

            // OAuth2 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(oauth2SuccessHandler)
            )

            // JWT 필터 배치
            .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, objectMapper), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
