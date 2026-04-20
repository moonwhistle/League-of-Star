package com.sang.smite.domain.account.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SocialProvider {
    GOOGLE("구글"),
    DISCORD("디스코드");

    private final String description;
}
