package com.sang.smite.auth.controller.response;

import com.sang.smite.domain.user.domain.User;

public record SignupResponse(
        Long id,
        String email,
        String nickname
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
