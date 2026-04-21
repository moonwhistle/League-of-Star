package com.sang.smite.common.exception;

import com.sang.smite.global.exception.BaseException;

/**
 * API 모듈에서 발생하는 공통 예외.
 */
public class ApiException extends BaseException {
    public ApiException(ApiErrorCode errorCode) {
        super(errorCode);
    }
}
