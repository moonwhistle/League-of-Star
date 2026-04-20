package com.sang.smite.common.exception;

import com.sang.smite.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Core 모듈에서 발생하는 비즈니스 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum CoreErrorCode implements BaseErrorCode {

    // Game (GAME_000)
    GAME_ROOM_FULL(400, "GAME_001", "게임방 인원이 초과되었습니다."),
    INVALID_GAME_STATE(400, "GAME_002", "현재 상태에서는 게임을 시작할 수 없습니다."),
    INCOMPLETE_PARTICIPANTS(400, "GAME_003", "참여자 인원 또는 상태가 올바르지 않습니다.");

    private final int httpStatus;
    private final String customCode;
    private final String message;

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String customCode() {
        return customCode;
    }

    @Override
    public String message() {
        return message;
    }
}
