package com.sang.smite.domain.game.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameStatus {
    IN_PROGRESS("진행 중"),
    FINISHED("종료됨"),
    ABORTED("중단됨");

    private final String description;
}
