package com.sang.smite.auth.controller.response;

import com.sang.smite.domain.user.domain.User;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String nickname
) {
    public static LoginResponse of(String accessToken, String refreshToken, User user) {
        return new LoginResponse(accessToken, refreshToken, user.getId(), user.getNickname());
    }
}
