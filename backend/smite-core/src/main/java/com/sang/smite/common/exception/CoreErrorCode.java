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

    GAME_ROOM_FULL(400, "CORE_001", "게임방 인원이 초과되었습니다.");

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
