package com.sang.leagueofstar.auth.service.dto;

public record TokenDto(
        String accessToken,
        String refreshToken
) {
}
