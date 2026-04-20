package com.sang.smite.domain.rank.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Tier {
    IRON("아이언", 1),
    BRONZE("브론즈", 2),
    SILVER("실버", 3),
    GOLD("골드", 4),
    PLATINUM("플래티넘", 5),
    EMERALD("에메랄드", 6),
    DIAMOND("다이아몬드", 7),
    MASTER("마스터", 8),
    GRANDMASTER("그랜드마스터", 9),
    CHALLENGER("챌린저", 10);

    private final String description;
    private final int level;
}
