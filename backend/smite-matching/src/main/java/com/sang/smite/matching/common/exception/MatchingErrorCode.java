package com.sang.smite.matching.common.exception;

import com.sang.smite.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 매칭 모듈에서 발생하는 공통 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum MatchingErrorCode implements BaseErrorCode {

    MATCH_REDIS_FETCH_ERROR(500, "MATCH_001", "Redis 매칭 대기열 조회 중 오류가 발생했습니다."),
    MATCH_LUA_SCRIPT_ERROR(500, "MATCH_002", "매칭 루아 스크립트 실행 또는 로드 중 오류가 발생했습니다."),
    ALREADY_IN_QUEUE(409, "MATCH_003", "이미 매칭 대기열에 진입한 유저입니다."),
    NOT_IN_QUEUE(400, "MATCH_004", "매칭 대기열에 진입하지 않은 유저입니다.");

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
