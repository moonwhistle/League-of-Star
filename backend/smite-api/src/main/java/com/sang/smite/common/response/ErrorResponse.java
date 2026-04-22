package com.sang.smite.common.response;

import com.sang.smite.global.exception.BaseErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 전역 공통 에러 응답 규격.
 * common.response 패키지에서 모든 API 응답 계약을 관리합니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String code;
    private String message;

    private ErrorResponse(BaseErrorCode errorCode) {
        this(errorCode.customCode(), errorCode.message(), errorCode.httpStatus());
    }

    private ErrorResponse(String code, String message, int status) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public static ErrorResponse of(BaseErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }

    public static ErrorResponse of(String code, String message, int status) {
        return new ErrorResponse(code, message, status);
    }
}
