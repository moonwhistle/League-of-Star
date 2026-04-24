package com.sang.smite.auth.service.dto;

public record TokenDto(
        String accessToken,
        String refreshToken
) {
}
