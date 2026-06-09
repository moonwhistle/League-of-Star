package com.sang.leagueofstar.common.exception;

import com.sang.leagueofstar.global.exception.BaseException;

/**
 * API 모듈에서 발생하는 공통 예외.
 */
public class ApiException extends BaseException {
    public ApiException(ApiErrorCode errorCode) {
        super(errorCode);
    }
}
