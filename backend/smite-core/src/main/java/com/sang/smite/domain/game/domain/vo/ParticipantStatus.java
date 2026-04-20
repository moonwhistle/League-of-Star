package com.sang.smite.domain.game.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게임 참여자의 현재 진행 상태를 나타냅니다.
 */
@Getter
@RequiredArgsConstructor
public enum ParticipantStatus {
    READY("준비"),
    PLAYING("경기중"),
    FINISHED("경기종료"),
    DISCONNECTED("연결끊김");

    private final String description;
}
