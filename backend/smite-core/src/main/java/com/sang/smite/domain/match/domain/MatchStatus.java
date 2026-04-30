package com.sang.smite.domain.match.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 유저의 매칭 프로세스 내 상태를 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum MatchStatus {
    MATCHING("매칭 중"),
    FOUND("매칭 성사(수락 대기)"),
    ACCEPTED("수락 완료"),
    DECLINED("거절 완료"),
    TIMEOUT("응답 시간 초과"),
    IN_GAME("게임 진행 중");

    private final String description;
}
