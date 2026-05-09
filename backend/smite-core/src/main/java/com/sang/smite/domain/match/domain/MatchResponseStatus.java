package com.sang.smite.domain.match.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 매칭 성사 후 각 유저의 수락/거절 응답 상태를 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum MatchResponseStatus {
    PENDING("응답 대기"),
    ACCEPTED("수락"),
    REJECTED("거절"),
    TIMEOUT("응답 시간 초과");

    private final String description;

    public boolean isResponded() {
        return this != PENDING;
    }
}
