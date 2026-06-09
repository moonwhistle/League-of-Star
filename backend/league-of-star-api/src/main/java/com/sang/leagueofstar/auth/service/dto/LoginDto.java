package com.sang.leagueofstar.auth.service.dto;

import com.sang.leagueofstar.domain.user.domain.User;

public record LoginDto(
        TokenDto tokens,
        User user
) {
}
