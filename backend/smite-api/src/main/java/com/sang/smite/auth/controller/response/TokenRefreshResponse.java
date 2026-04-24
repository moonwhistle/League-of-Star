package com.sang.smite.auth.controller.response;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken
) {
    public static TokenRefreshResponse of(String accessToken, String refreshToken) {
        return new TokenRefreshResponse(accessToken, refreshToken);
    }
}
