package com.sang.smite.domain.rank.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Division {
    I(1),
    II(2),
    III(3),
    IV(4);

    private final int value;
}
