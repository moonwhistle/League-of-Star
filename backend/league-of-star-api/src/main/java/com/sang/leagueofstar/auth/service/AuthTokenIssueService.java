package com.sang.leagueofstar.auth.service;

import com.sang.leagueofstar.auth.domain.RefreshToken;
import com.sang.leagueofstar.auth.infrastructure.jwt.JwtTokenProvider;
import com.sang.leagueofstar.auth.infrastructure.token.RefreshTokenRepository;
import com.sang.leagueofstar.auth.service.dto.TokenDto;
import com.sang.leagueofstar.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthTokenIssueService {

    private static final long MILLIS_TO_SECONDS = 1000L;

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenDto issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());

        refreshTokenRepository.save(RefreshToken.of(
                user.getId(),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpirationMs() / MILLIS_TO_SECONDS
        ));

        return new TokenDto(accessToken, refreshToken);
    }
}
