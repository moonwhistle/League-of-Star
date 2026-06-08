package com.sang.leagueofstar.domain.user.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("활성"),
    WITHDRAWN("탈퇴 요청"),
    ANONYMIZED("익명화 완료");

    private final String description;
}
