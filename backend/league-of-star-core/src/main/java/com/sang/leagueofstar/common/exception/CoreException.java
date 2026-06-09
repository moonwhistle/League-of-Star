package com.sang.leagueofstar.common.exception;

import com.sang.leagueofstar.global.exception.BaseException;

/**
 * Core 모듈에서 발생하는 비즈니스 예외.
 */
public class CoreException extends BaseException {

    public CoreException(CoreErrorCode errorCode) {
        super(errorCode);
    }
}
