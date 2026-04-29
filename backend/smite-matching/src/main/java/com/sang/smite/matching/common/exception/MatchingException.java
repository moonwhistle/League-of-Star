package com.sang.smite.matching.common.exception;

import com.sang.smite.global.exception.BaseException;

/**
 * 매칭 모듈에서 발생하는 예외의 기본 클래스입니다.
 */
public class MatchingException extends BaseException {
    public MatchingException(MatchingErrorCode errorCode) {
        super(errorCode);
    }

    public MatchingException(MatchingErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
