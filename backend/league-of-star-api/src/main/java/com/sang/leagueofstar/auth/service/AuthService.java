package com.sang.leagueofstar.auth.service;

import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.leagueofstar.auth.service.dto.LoginDto;
import com.sang.leagueofstar.auth.service.dto.TokenDto;
import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserCommandService;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.auth.infrastructure.token.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserReadService userReadService;
    private final UserCommandService userCommandService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenIssueService authTokenIssueService;

    @Transactional
    public User signUp(String email, String password, String nickname) {
        if (userReadService.existsByEmail(email)) {
            throw new ApiException(ApiErrorCode.AUTH_DUPLICATE_EMAIL);
        }

        if (userReadService.existsByNickname(nickname)) {
            throw new ApiException(ApiErrorCode.AUTH_DUPLICATE_NICKNAME);
        }

        String encodedPassword = passwordEncoder.encode(password);
        return userCommandService.signup(email, encodedPassword, nickname);
    }

    @Transactional
    public LoginDto login(String email, String password) {
        // 1. 사용자 조회
        User user = userReadService.findByEmail(email)
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_LOGIN_FAILED));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ApiException(ApiErrorCode.AUTH_LOGIN_FAILED);
        }

        // 3. 토큰 발급
        TokenDto tokens = authTokenIssueService.issueTokens(user);

        return new LoginDto(tokens, user);
    }

    @Transactional
    public TokenDto refresh(String refreshToken) {
        // 1. 토큰 유효성 및 만료 확인
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }

        // 2. Redis에 저장된 토큰과 비교
        refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_INVALID_REFRESH_TOKEN));

        // 3. 사용자 조회
        User user = userReadService.findByEmail(jwtTokenProvider.getAuthentication(refreshToken).getName())
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_INVALID_REFRESH_TOKEN));

        // 4. 새로운 토큰 쌍 발급
        return authTokenIssueService.issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

}
