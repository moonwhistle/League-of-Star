package com.sang.smite.domain.game.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameStatus {
    READY("대기 중"),
    IN_PROGRESS("진행 중"),
    FINISHED("종료됨"),
    ABORTED("중단됨");

    private final String description;

    public boolean isReady() {
        return this == READY;
    }

    public boolean isInProgress() {
        return this == IN_PROGRESS;
    }

    public boolean isFinished() {
        return this == FINISHED;
    }
}
