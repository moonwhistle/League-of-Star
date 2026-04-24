package com.sang.smite.auth.service.dto;

import com.sang.smite.domain.user.domain.User;

public record LoginDto(
        TokenDto tokens,
        User user
) {
}
