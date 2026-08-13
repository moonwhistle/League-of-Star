package com.sang.leagueofstar.auth.security.config;

import com.sang.leagueofstar.auth.service.CustomOAuth2UserService;
import com.sang.leagueofstar.common.path.customgame.CustomGamePath;
import com.sang.leagueofstar.common.path.security.SecurityPath;
import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.leagueofstar.auth.filter.JwtAuthenticationFilter;
import com.sang.leagueofstar.auth.handler.JwtAuthenticationExceptionHandler;
import com.sang.leagueofstar.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final JwtAuthenticationExceptionHandler authenticationExceptionHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtTokenProvider tokenProvider,
            JwtAuthenticationExceptionHandler authenticationExceptionHandler,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler oauth2SuccessHandler,
            ObjectMapper objectMapper,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        this.tokenProvider = tokenProvider;
        this.authenticationExceptionHandler = authenticationExceptionHandler;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())

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
                .requestMatchers(
                        HttpMethod.GET,
                        CustomGamePath.CUSTOM_ROOM_PUBLIC_LIST,
                        CustomGamePath.CUSTOM_ROOM_INVITE_PREVIEW_PATTERN
                ).permitAll()
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
