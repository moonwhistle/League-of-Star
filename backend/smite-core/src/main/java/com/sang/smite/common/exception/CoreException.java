package com.sang.smite.common.exception;

import com.sang.smite.global.exception.BaseException;

/**
 * Core 모듈에서 발생하는 비즈니스 예외.
 */
public class CoreException extends BaseException {

    public CoreException(CoreErrorCode errorCode) {
        super(errorCode);
    }
}
